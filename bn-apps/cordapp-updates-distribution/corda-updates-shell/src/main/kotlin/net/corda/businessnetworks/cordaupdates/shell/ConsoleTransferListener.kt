package net.corda.businessnetworks.cordaupdates.shell

import org.eclipse.aether.transfer.AbstractTransferListener
import org.eclipse.aether.transfer.MetadataNotFoundException
import org.eclipse.aether.transfer.TransferEvent
import org.eclipse.aether.transfer.TransferResource
import org.slf4j.Logger
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ConsoleTransferListener @JvmOverloads constructor(val logger : Logger) : AbstractTransferListener() {
    private val downloads = ConcurrentHashMap<TransferResource, Long>()
    private var lastLength : Int = 0


    override fun transferInitiated(event : TransferEvent) {
        val message = when (event.requestType) {
            TransferEvent.RequestType.PUT -> "Uploading"
            else -> "Downloading"
        }
        logger.info(message + ": " + event.resource.repositoryUrl + event.resource.resourceName)
    }

    override fun transferProgressed(event : TransferEvent) {
        val resource = event.resource
        downloads[resource] = event.transferredBytes

        val buffer = StringBuilder(64)

        downloads.forEach {
            val total = it.key.contentLength
            buffer.append(getStatus(it.value, total)).append("  ")
        }

        val pad = lastLength - buffer.length
        lastLength = buffer.length
        pad(buffer, pad)
        buffer.append('\r')

        logger.info(buffer.toString())
    }

    private fun getStatus(complete : Long, total : Long) : String {
        return when {
            total >= 1024 -> toKB(complete).toString() + "/" + toKB(total) + " KB "
            total >= 0 -> complete.toString() + "/" + total + " B "
            complete >= 1024 -> toKB(complete).toString() + " KB "
            else -> complete.toString() + " B "
        }
    }

    private fun pad(buffer : StringBuilder, spaces : Int) {
        var spaces = spaces
        val block = "                                        "
        while (spaces > 0) {
            val n = Math.min(spaces, block.length)
            buffer.append(block, 0, n)
            spaces -= n
        }
    }

    override fun transferSucceeded(event : TransferEvent) {
        transferCompleted(event)

        val resource = event.resource
        val contentLength = event.transferredBytes
        if (contentLength >= 0) {
            val type = when (event.requestType) {
                 TransferEvent.RequestType.PUT -> "Uploaded"
                else -> "Downloaded"
            }
            val len = when {
                contentLength >= 1024 -> "${toKB(contentLength)} KB"
                else -> "$contentLength B"
            }

            var throughput = ""
            val duration = System.currentTimeMillis() - resource.transferStartTime
            if (duration > 0) {
                val bytes = contentLength - resource.resumeOffset
                val format = DecimalFormat("0.0", DecimalFormatSymbols(Locale.ENGLISH))
                val kbPerSec = bytes / 1024.0 / (duration / 1000.0)
                throughput = " at ${format.format(kbPerSec)} KB/sec"
            }

            logger.info("$type: ${resource.repositoryUrl}${resource.resourceName} ($len$throughput)")
        }
    }

    override fun transferFailed(event : TransferEvent) {
        transferCompleted(event)
        if (event.exception !is MetadataNotFoundException) {
            logger.error("Transfer failed", event.exception)
        }
    }

    private fun transferCompleted(event : TransferEvent) {
        downloads.remove(event.getResource())

        val buffer = StringBuilder(64)
        pad(buffer, lastLength)
        buffer.append('\r')
        logger.info(buffer.toString())
    }

    override fun transferCorrupted(event : TransferEvent) {
        logger.error("Transfer corrupted", event.exception)
    }

    private fun toKB(bytes : Long) : Long {
        return (bytes + 1023) / 1024
    }
}
