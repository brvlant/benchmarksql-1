/*
 * jTPCC - BenchmarkSQL main class
 *
 */
import org.apache.log4j.*;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.text.*;
import java.util.regex.Pattern;
import java.lang.Integer;
import java.lang.Double;
import java.lang.Boolean;

public class jTPCC
{
    private static org.apache.log4j.Logger log = Logger.getLogger(jTPCC.class);

    private long		now;

    public  jTPCCRandom		rnd;
    public  String		applicationName;
    public  String		iDBType;
    public  String		iConn;
    public  String		iUser;
    public  String		iPassword;

    public  static int		loadWarehouses;
    public  static int		loadNuRandCLast;
    public  static int		loadNuRandCC_ID;
    public  static int		loadNuRandCI_ID;

    public  static int		dbType;
    public  static int		numWarehouses;
    public  static int		numMonkeys;
    public  static int		numSUTThreads;
    public  static int		maxDeliveryBGThreads;
    public  static int		maxDeliveryBGPerWH;
    public  static int		runMins;
    public  static int		rampupMins;
    public  static int		rampupSUTMins;
    public  static int		rampupTerminalMins;
    public  static int		reportIntervalSecs;
    public  static double	restartSUTThreadProb;
    public  static double	keyingTimeMultiplier;
    public  static double	thinkTimeMultiplier;
    public  static boolean	traceTerminalIO = false;

    public  static int		sutThreadDelay;
    public  static int		terminalDelay;

    public  static double	newOrderWeight;
    public  static double	paymentWeight;
    public  static double	orderStatusWeight;
    public  static double	deliveryWeight;
    public  static double	stockLevelWeight;

    private OSCollector		osCollector = null;
    private jTPCCTData		terminal_data[];
    private Thread		scheduler_thread;
    public  jTPCCScheduler	scheduler;
    public  jTPCCSUT		systemUnderTest;
    public  jTPCCMonkey		monkeys;

    public  static String		resultDirectory = null;
    public  static String		osCollectorScript = null;
    private static String		resultDirName = null;
    private static BufferedWriter	resultCSV = null;
    private static BufferedWriter	runInfoCSV = null;
    public  static int			runID = 0;
    public  static long			csv_begin;
    public  static long			result_begin;
    public  static long			result_end;

    public static void main(String args[])
	throws FileNotFoundException
    {
	PropertyConfigurator.configure("log4j.properties");
	new jTPCC();
    }

    private String getProp (Properties p, String pName)
    {
	String prop =  p.getProperty(pName);
	log_info("" + pName + "=" + prop);
	return(prop);
    }

    public jTPCC()
	throws FileNotFoundException
    {
	StringBuilder	sb = new StringBuilder();
	Formatter	fmt = new Formatter(sb);

	// load the ini file
	Properties ini = new Properties();
	try {
	    ini.load( new FileInputStream(System.getProperty("prop")));
	} catch (IOException e) {
	    log_error("could not load properties file");
	}

	/*
	 * Get all the configuration settings
	 */
	log_info("");
	log_info("+-------------------------------------------------------------+");
	log_info("     BenchmarkSQL v" + jTPCCConfig.JTPCCVERSION);
	log_info("+-------------------------------------------------------------+");
	log_info(" (c) 2003, Raul Barbosa");
	log_info(" (c) 2004-2014, Denis Lussier");
	log_info(" (c) 2016-2019, Jan Wieck");
	log_info("+-------------------------------------------------------------+");
	log_info("");
	String  iDBType		= getProp(ini,"db");
	String	iDriver		= getProp(ini,"driver");
	applicationName		= getProp(ini,"application");
	iConn			= getProp(ini,"conn");
	iUser			= getProp(ini,"user");
	iPassword		= ini.getProperty("password");

	log_info("");
	numWarehouses		= Integer.parseInt(getProp(ini, "warehouses"));
	numMonkeys		= Integer.parseInt(getProp(ini, "monkeys"));
	numSUTThreads		= Integer.parseInt(getProp(ini, "sutThreads"));
	maxDeliveryBGThreads	= Integer.parseInt(getProp(ini, "maxDeliveryBGThreads"));
	maxDeliveryBGPerWH	= Integer.parseInt(getProp(ini, "maxDeliveryBGPerWarehouse"));
	rampupMins		= Integer.parseInt(getProp(ini, "rampupMins"));
	runMins			= Integer.parseInt(getProp(ini, "runMins"));
	rampupSUTMins		= Integer.parseInt(getProp(ini, "rampupSUTMins"));
	rampupTerminalMins	= Integer.parseInt(getProp(ini, "rampupTerminalMins"));
	reportIntervalSecs	= Integer.parseInt(getProp(ini, "reportIntervalSecs"));
	restartSUTThreadProb	= Double.parseDouble(getProp(ini, "restartSUTThreadProbability"));
	keyingTimeMultiplier	= Double.parseDouble(getProp(ini, "keyingTimeMultiplier"));
	thinkTimeMultiplier	= Double.parseDouble(getProp(ini, "thinkTimeMultiplier"));
	traceTerminalIO		= Boolean.parseBoolean(getProp(ini, "traceTerminalIO"));
	log_info("");
	paymentWeight		= Double.parseDouble(getProp(ini, "paymentWeight"));
	orderStatusWeight	= Double.parseDouble(getProp(ini, "orderStatusWeight"));
	deliveryWeight		= Double.parseDouble(getProp(ini, "deliveryWeight"));
	stockLevelWeight	= Double.parseDouble(getProp(ini, "stockLevelWeight"));
	newOrderWeight		= 100.0 - paymentWeight - orderStatusWeight -
				  deliveryWeight - stockLevelWeight;
	if (newOrderWeight < 0.0)
	{
	    log_error("newOrderWeight is below zero");
	    return;
	}
	fmt.format("newOrderWeight=%.3f", newOrderWeight);
	log_info(sb.toString());
	log_info("");

	sutThreadDelay = (rampupSUTMins * 60000) / numSUTThreads;
	terminalDelay = (rampupTerminalMins * 60000) / (numWarehouses * 10);

	if (iDBType.equals("oracle"))
	    dbType = jTPCCConfig.DB_ORACLE;
	else if (iDBType.equals("postgres"))
	    dbType = jTPCCConfig.DB_POSTGRES;
	else if (iDBType.equals("firebird"))
	    dbType = jTPCCConfig.DB_FIREBIRD;
	else if (iDBType.equals("mariadb"))
	    dbType = jTPCCConfig.DB_MARIADB;
	else if (iDBType.equals("transact-sql"))
	    dbType = jTPCCConfig.DB_TSQL;
	else
	{
	    log.error("unknown database type '" + iDBType + "'");
	    return;
	}


	/*
	 * Load the requested JDBC driver
	 */
	try
	{
	    String		driver = iDriver;

	    log_info("Loading database driver: \'" + driver + "\'...");
	    Class.forName(iDriver);
	}
	catch(Exception ex)
	{
	    log_error("Unable to load the database driver!");
	    log_error(ex.toString());
	    return;
	}

	/*
	 * Get the load configuration from the database
	 */
	try
	{
	    Connection		dbConn;
	    Properties		dbProps;
	    ResultSet		rs;
	    PreparedStatement	cfgStmt;

	    dbProps = new Properties();
	    dbProps.setProperty("user", iUser);
	    dbProps.setProperty("password", iPassword);
	    dbConn = DriverManager.getConnection(iConn, dbProps);
	    dbConn.setAutoCommit(false);

	    cfgStmt = dbConn.prepareStatement("SELECT cfg_value FROM bmsql_config WHERE cfg_name = ?");

	    cfgStmt.setString(1, "warehouses");
	    rs = cfgStmt.executeQuery();
	    rs.next();
	    loadWarehouses = Integer.parseInt(rs.getString("cfg_value"));

	    cfgStmt.setString(1, "nURandCLast");
	    rs = cfgStmt.executeQuery();
	    rs.next();
	    loadNuRandCLast = Integer.parseInt(rs.getString("cfg_value"));

	    cfgStmt.setString(1, "nURandCC_ID");
	    rs = cfgStmt.executeQuery();
	    rs.next();
	    loadNuRandCC_ID = Integer.parseInt(rs.getString("cfg_value"));

	    cfgStmt.setString(1, "nURandCI_ID");
	    rs = cfgStmt.executeQuery();
	    rs.next();
	    loadNuRandCI_ID = Integer.parseInt(rs.getString("cfg_value"));

	    cfgStmt.close();
	    dbConn.rollback();
	    dbConn.close();
	}
	catch(Exception ex)
	{
	    log_error("Unable to read load configuration");
	    log_error(ex.toString());
	    return;
	}

	/*
	 * Check that we support the requested application implementation
	 */
	if (!applicationName.equals("Generic") &&
	    !applicationName.equals("PostgreSQLStoredProc") &&
	    !applicationName.equals("OracleStoredProc"))
	{
	    log.error("Unknown application name '"+applicationName+"'");
	    return;
	}

	String  resultDirectory     = getProp(ini, "resultDirectory");
	String  osCollectorScript   = getProp(ini, "osCollectorScript");
	/*
	if (true)
	{
	    String csv_fname = "result.csv";
	    csv_result = new PrintWriter(new FileOutputStream(csv_fname));
	    csv_result.println("type,startts,endts,startms,endms," +
			       "delayms,latencyms,rbk,error,message");
	}
	*/
	if (resultDirectory != null)
	{
	    StringBuffer        sbRes = new StringBuffer();
	    Formatter           fmtRes = new Formatter(sbRes);
	    Pattern             p = Pattern.compile("%t");
	    Calendar            cal = Calendar.getInstance();

	    String              iRunID;

	    iRunID = System.getProperty("runID");
	    if (iRunID != null)
	    {
		runID = Integer.parseInt(iRunID);
	    }

	    /*
	     * Split the resultDirectory into strings around
	     * patterns of %t and then insert date/time formatting
	     * based on the current time. That way the resultDirectory
	     * in the properties file can have date/time format
	     * elements like in result_%tY-%tm-%td to embed the current
	     * date in the directory name.
	     */
	    String[] parts = p.split(resultDirectory, -1);
	    sbRes.append(parts[0]);
	    for (int i = 1; i < parts.length; i++)
	    {
		fmtRes.format("%t" + parts[i].substring(0, 1), cal);
		sbRes.append(parts[i].substring(1));
	    }
	    resultDirName = sbRes.toString();
	    File resultDir = new File(resultDirName);
	    File resultDataDir = new File(resultDir, "data");

	    // Create the output directory structure.
	    if (!resultDir.mkdir())
	    {
		log.error("Failed to create directory '" +
			  resultDir.getPath() + "'");
		System.exit(1);
	    }
	    if (!resultDataDir.mkdir())
	    {
		log.error("Failed to create directory '" +
			  resultDataDir.getPath() + "'");
		System.exit(1);
	    }

	    // Copy the used properties file into the resultDirectory.
	    try
	    {
		copyFile(new File(System.getProperty("prop")),
			 new File(resultDir, "run.properties"));
	    }
	    catch (Exception e)
	    {
		log.error(e.getMessage());
		System.exit(1);
	    }
	    log.info("main, copied " + System.getProperty("prop") +
		     " to " + new File(resultDir, "run.properties").getPath());

	    // Create the runInfo.csv file.
	    String runInfoCSVName = new File(resultDataDir, "runInfo.csv").getPath();
	    try
	    {
		runInfoCSV = new BufferedWriter(
			    new FileWriter(runInfoCSVName));
		runInfoCSV.write("runID,dbType,jTPCCVersion,application," +
				 "rampupMins,runMins," +
				 "loadWarehouses,runWarehouses,numSUTThreads," +
				 "maxDeliveryBGThreads,maxDeliveryBGPerWarehouse," +
				 "restartSUTThreadProbability," +
				 "thinkTimeMultiplier,keyingTimeMultiplier\n");
		runInfoCSV.write(runID + "," +
				 iDBType + "," +
				 jTPCCConfig.JTPCCVERSION + "," +
				 applicationName + "," +
				 rampupMins + "," +
				 runMins + "," +
				 loadWarehouses + "," +
				 numWarehouses + "," +
				 numSUTThreads + "," +
				 maxDeliveryBGThreads + "," +
				 maxDeliveryBGPerWH + "," +
				 restartSUTThreadProb + "," +
				 thinkTimeMultiplier + "," +
				 keyingTimeMultiplier + "\n");
		runInfoCSV.close();
	    }
	    catch (IOException e)
	    {
		log.error(e.getMessage());
		System.exit(1);
	    }
	    log.info("main, created " + runInfoCSVName + " for runID " +
		     runID);

	    // Open the per transaction result.csv file.
	    String resultCSVName = new File(resultDataDir, "result.csv").getPath();
	    try
	    {
		resultCSV = new BufferedWriter(new FileWriter(resultCSVName));
		resultCSV.write("run,ttype,startts,endts,startms,endms,delayms," +
				"latencyms,rbk,error\n");
	    }
	    catch (IOException e)
	    {
		log.error(e.getMessage());
		System.exit(1);
	    }
	    log.info("main, writing per transaction results to " +
		     resultCSVName);

	    if (osCollectorScript != null)
	    {
	    	osCollector = new OSCollector(getProp(ini, "osCollectorScript"),
				runID,
				Integer.parseInt(getProp(ini, "osCollectorInterval")),
				getProp(ini, "osCollectorSSHAddr"),
				getProp(ini, "osCollectorDevices"),
				resultDataDir, log);
	    }

	    log.info("main,");
	}

	/* Initialize the random number generator and report C values. */
	rnd = new jTPCCRandom(loadNuRandCLast);
	log_info("");
	log_info("C value for nURandCLast at load: " + loadNuRandCLast);
	log_info("C value for nURandCLast this run: " + rnd.getNURandCLast());
	log_info("");

	terminal_data = new jTPCCTData[numWarehouses * 10];

	/* Create the scheduler. */
	scheduler = new jTPCCScheduler(this);
	scheduler_thread = new Thread(this.scheduler);
	scheduler_thread.start();

	/*
	 * Create the SUT and schedule the launch of the SUT threads.
	 */
	now = System.currentTimeMillis();
	systemUnderTest = new jTPCCSUT(this);
	for (int t = 0; t < numSUTThreads; t++)
	{
	    jTPCCTData	    sut_launch_tdata;
	    sut_launch_tdata = new jTPCCTData();

	    /*
	     * We abuse the term_w_id to communicate which of the
	     * SUT threads to start.
	     */
	    sut_launch_tdata.term_w_id = t;
	    scheduler.at(now + t * sutThreadDelay,
			 jTPCCScheduler.SCHED_SUT_LAUNCH,
			 sut_launch_tdata);
	}

	/*
	 * Launch the threads that generate the terminal input data.
	 */
	monkeys = new jTPCCMonkey(this);

	/*
	 * Create all the Terminal data sets and schedule their
	 * launch. We only assign their fixed TERM_W_ID is
	 * TERM_D_ID (for stock level transactions) here. Once the
	 * scheduler is actually launching them according to their
	 * delay, the trained monkeys will fill in real data, send
	 * them back into the scheduler queue to the flow to the
	 * client threads performing the real DB work.
	 */
	for (int t = 0; t < numWarehouses * 10; t++)
	{
	    terminal_data[t] = new jTPCCTData();
	    terminal_data[t].term_w_id = (t / 10) + 1;
	    terminal_data[t].term_d_id = (t % 10) + 1;
	    terminal_data[t].trans_type = jTPCCTData.TT_NONE;
	    terminal_data[t].trans_due = now + t * terminalDelay;
	    terminal_data[t].trans_start = terminal_data[t].trans_due;
	    terminal_data[t].trans_end = terminal_data[t].trans_due;
	    terminal_data[t].trans_error = false;

	    scheduler.at(terminal_data[t].trans_due,
			 jTPCCScheduler.SCHED_TERM_LAUNCH,
			 terminal_data[t]);
	}

	/*
	 * Schedule the special events to begin measurement (end of
	 * rampup time), to shut down the system and to print messages
	 * when the terminals and SUT threads have all been started.
	 */
	csv_begin	= now;
	result_begin	= now + rampupMins * 60000;
	result_end	= result_begin + runMins * 60000;

	this.scheduler.at(result_begin,
			  jTPCCScheduler.SCHED_BEGIN,
			  new jTPCCTData());
	this.scheduler.at(result_end,
			  jTPCCScheduler.SCHED_END,
			  new jTPCCTData());
	this.scheduler.at(result_end + 10000,
			  jTPCCScheduler.SCHED_DONE,
			  new jTPCCTData());
	this.scheduler.at(now + (numWarehouses * 10) * terminalDelay,
			  jTPCCScheduler.SCHED_TERM_LAUNCH_DONE,
			  new jTPCCTData());
	this.scheduler.at(now + numSUTThreads * sutThreadDelay,
			  jTPCCScheduler.SCHED_SUT_LAUNCH_DONE,
			  new jTPCCTData());
	if (reportIntervalSecs > 0)
	{
	    this.scheduler.at(now + reportIntervalSecs * 1000,
			      jTPCCScheduler.SCHED_REPORT,
			      new jTPCCTData());
	}

	try {
	    scheduler_thread.join();
	    log_info("scheduler returned");
	}
	catch(InterruptedException e)
	{
	    log_error("InterruptedException: " + e.getMessage());
	}

	/*
	 * Time to stop input data generation.
	 */
	monkeys.terminate();
	log_info("all simulated terminals ended");

	/*
	 * Stop the SUT.
	 */
	systemUnderTest.terminate();
	log_info("all SUT threads ended");

	/*
	 * Report final transaction statistics.
	 */
	monkeys.reportStatistics();

	/*
	 * Close the per transaction CSV result
	 */
	if (resultCSV != null)
	{
	    try {
		log_info("per transaction result file finished");
		resultCSV.close();
	    }
	    catch (Exception e)
	    {
		log.error(e.getMessage());
	    }
	}

	/*
	 * Stop the OS stats collector
	 */
	if (osCollector != null)
	{
	    osCollector.stop();
	    osCollector = null;
	    log_info("OS Collector stopped");
	}
    }

    public jTPCCApplication getApplication()
    {
	if (applicationName.equals("Generic"))
	    return new AppGeneric();
	if (applicationName.equals("PostgreSQLStoredProc"))
	    return new AppPostgreSQLStoredProc();
	if (applicationName.equals("OracleStoredProc"))
	    return new AppOracleStoredProc();

	return new jTPCCApplication();
    }

    public static void csv_result_write(String line)
    {
	if (resultCSV != null)
	{
	    try {
		resultCSV.write(line);
	    }
	    catch (Exception e)
	    {
	    }
	}
    }

    private void log_trace(String message)
    {
	log.trace("main, " + message);
    }

    private void log_info(String message)
    {
	log.info("main, " + message);
    }

    private void log_error(String message)
    {
	log.error("main, "+ message);
    }

    private void exit()
    {
	System.exit(0);
    }

    private String getCurrentTime()
    {
	return jTPCCConfig.dateFormat.format(new java.util.Date());
    }

    private String getFileNameSuffix()
    {
	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
	return dateFormat.format(new java.util.Date());
    }

    private void copyFile(File in, File out)
	throws FileNotFoundException, IOException
    {
	FileInputStream strIn = new FileInputStream(in);
	FileOutputStream strOut = new FileOutputStream(out);
	byte buf[] = new byte[65536];

	int len = strIn.read(buf);
	while (len > 0)
	{
	    strOut.write(buf, 0, len);
	    len = strIn.read(buf);
	}

	strOut.close();
	strIn.close();
    }
}
