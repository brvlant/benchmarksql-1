# ----
# $1 is the properties file
# ----
PROPS="$1"
if [ ! -f ${PROPS} ] ; then
    echo "${PROPS}: no such file" >&2
    exit 1
fi

# ----
# getProp()
#
#   Get a config value from the properties file.
# ----
function getProp()
{
    grep "^${1}=" ${PROPS} | sed -e "s/^${1}=//" -e 's/\s*$//'
}

# ----
# getCP()
#
#   Determine the CLASSPATH based on the database system.
# ----
function setCP()
{
    case "$(getProp db)" in
	oracle)
	    cp="../lib/oracle/*"
	    if [ ! -z "${ORACLE_HOME}" -a -d ${ORACLE_HOME}/lib ] ; then
		cp="${cp}:${ORACLE_HOME}/lib/*"
	    fi
	    cp="${cp}:../lib/*"
	    ;;
	postgres)
	    cp="../lib/postgres/*:../lib/*"
	    ;;
	firebird)
	    cp="../lib/firebird/*:../lib/*"
	    ;;
	mariadb)
	    cp="../lib/mariadb/*:../lib/*"
	    ;;
	transact-sql)
	    cp="../lib/transact-sql/*:../lib/*"
	    ;;
    esac
    myCP="../extra_lib/*:.:${cp}:../dist/*"
    export myCP
}

# ----
# Make sure that the properties file does have db= and the value
# is a database, we support.
# ----
db=$(getProp db)
case "${db}" in
    oracle|postgres|firebird|mariadb|transact-sql)
	;;
    "")	echo "ERROR: missing db= config option in ${PROPS}" >&2
	exit 1
	;;
    *)	echo "ERROR: unsupported database type db=${db} in ${PROPS}" >&2
	exit 1
	;;
esac
