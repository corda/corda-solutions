package com.r3.businessnetworks.vaultrecycler

import com.sun.xml.internal.messaging.saaj.util.ByteInputStream
import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream
import net.corda.core.crypto.SecureHash
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class GarbagePersisterTest {
    private val persister = GarbagePersister("")

    @Test
    fun doTest() {
        val beforeSerialization = RecyclableData(
                listOf(SecureHash.sha256(randomBytes(20)), SecureHash.sha256(randomBytes(20))),
                listOf(SecureHash.sha256(randomBytes(20)), SecureHash.sha256(randomBytes(20)))
        )

        val byteOs = ByteOutputStream()
        persister.write(beforeSerialization) { byteOs }

        val inputStream : ByteInputStream = byteOs.newInputStream()!!

        val afterSerialization = persister.read(inputStream)

        assertEquals(beforeSerialization, afterSerialization)
    }

    private fun randomBytes(num : Int) : ByteArray {
        val random = Random()
        val bytes = ByteArray(num)
        random.nextBytes(bytes)
        return bytes
    }
}

