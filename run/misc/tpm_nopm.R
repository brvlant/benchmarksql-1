# ----
# R graph to show tpmC and tpmTOTAL.
# ----

# ----
# Read the runInfo.csv file.
# ----
runInfo <- read.csv("data/runInfo.csv", head=TRUE)

# ----
# Determine the grouping interval in seconds based on the
# run duration.
# ----
xmin <- @SKIP@
if (xmin < 0) {
    xmin <- runInfo$rampupMins
}
xmax <- runInfo$runMins + runInfo$rampupMins
for (interval in c(1, 2, 5, 10, 20, 60, 120, 300, 600)) {
    if ((xmax * 60) / interval <= 1000) {
        break
    }
}
idiv <- interval * 1000.0
skip <- xmin * 60000
cutoff <- xmax * 60000

# ----
# Read the result.csv and then filter the raw data
# for != DELIVERY_BG and == NEW_ORDER transactions.
# ----
data1 <- read.csv("data/result.csv", head=TRUE)
if (is.na(data1[nrow(data1),]$error))
{
    data1 <- data1[0:(nrow(data1) - 1), ]
}
data1 <- data1[data1$endms >= skip & data1$endms < cutoff, ]
total1 <- data1[data1$ttype != 'DELIVERY_BG', ]
neworder1 <- data1[data1$ttype == 'NEW_ORDER', ]

# ----
# Aggregate the counts of both data sets grouped by second.
# ----
countTotal <- setNames(aggregate(total1$latency, list(elapsed=trunc(total1$endms / idiv) * idiv), NROW),
		   c('elapsed', 'count'));
countNewOrder <- setNames(aggregate(neworder1$latency, list(elapsed=trunc(neworder1$endms / idiv) * idiv), NROW),
		   c('elapsed', 'count'));

countTotal
countNewOrder

# ----
# Determine the ymax by increasing in sqrt(2) steps until the
# maximum of tpmTOTAL fits, then make sure that we have at least
# 1.2 times that to give a little head room for the legend.
# ----
ymax_count <- max(countTotal$count) * 60.0 / interval
ymax <- 1
sqrt2 <- sqrt(2.0)
while (ymax < ymax_count) {
    ymax <- ymax * sqrt2
}
if (ymax < (ymax_count * 1.2)) {
    ymax <- ymax * 1.2
}



# ----
# Start the output image.
# ----
svg("tpm_nopm.svg", width=@WIDTH@, height=@HEIGHT@, pointsize=@POINTSIZE@)
par(mar=c(4,4,4,4), xaxp=c(10,200,19))

# ----
# Plot the tpmTOTAL graph.
# ----
plot (
	countTotal$elapsed / 60000.0, countTotal$count * 60.0 / interval,
	type='l', col="blue3", lwd=2,
	axes=TRUE,
	xlab="Elapsed Minutes",
	ylab="Transactions per Minute",
	xlim=c(xmin, xmax),
	ylim=c(0, ymax)
)

# ----
# Plot the tpmC graph.
# ----
par (new=T)
plot (
	countNewOrder$elapsed / 60000.0, countNewOrder$count * 60.0 / interval,
	type='l', col="red3", lwd=2,
	axes=FALSE,
	xlab="",
	ylab="",
	xlim=c(xmin, xmax),
	ylim=c(0, ymax)
)

# ----
# Add legend, title and other decorations.
# ----
legend ("topleft",
	c("tpmTOTAL", "tpmC (NewOrder only)"),
	fill=c("blue3", "red3"))
title (main=c(
    paste0("Run #", runInfo$runID, " of BenchmarkSQL v", runInfo$jTPCCVersion),
    "Transactions per Minute"
    ))
grid()
box()
