package com.r3.businessnetworks.vaultrecycler

import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.internal.AMQP_P2P_CONTEXT
import net.corda.serialization.internal.AMQP_STORAGE_CONTEXT
import net.corda.serialization.internal.CordaSerializationMagic
import net.corda.serialization.internal.SerializationFactoryImpl
import net.corda.serialization.internal.amqp.AbstractAMQPSerializationScheme
import net.corda.serialization.internal.amqp.amqpMagic
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class GarbagePersister(private val path : String) {

    init {
        initialiseSerialisation()
    }


    fun write(recyclableData : RecyclableData, streamFactory : (String) -> OutputStream) {
        val payload = recyclableData.serialize()
        val hash = SecureHash.sha256(payload.bytes)
        streamFactory(hash.toString()).use { it.write(payload.bytes) }
    }

    fun read(inputStream : InputStream) : RecyclableData {
        return inputStream.use {
            val length = it.available()
            val byteArray = ByteArray(length)
            it.read(byteArray)
            byteArray.deserialize()
        }
    }


    fun writeToFile(recyclableData : RecyclableData) = write(recyclableData) {
        hash -> FileOutputStream("$path/$hash")
    }

    fun readFromFile(hash : String) : RecyclableData = read(FileInputStream("$path/$hash"))

    private fun Any.secureHash() : SecureHash = SecureHash.sha256(this.serialize().bytes)

    private fun initialiseSerialisation() {
        if (_contextSerializationEnv.get() == null) {
            _contextSerializationEnv.set(SerializationEnvironment.with(
                    SerializationFactoryImpl().apply {
                        registerScheme(AMQPInspectorSerializationScheme)
                    },
                    p2pContext = AMQP_P2P_CONTEXT.withLenientCarpenter(),
                    storageContext = AMQP_STORAGE_CONTEXT.withLenientCarpenter()
            ))
        }
    }
}

private object AMQPInspectorSerializationScheme : AbstractAMQPSerializationScheme(emptyList()) {
    override fun canDeserializeVersion(magic : CordaSerializationMagic, target : SerializationContext.UseCase) : Boolean {
        return magic == amqpMagic
    }

    override fun rpcClientSerializerFactory(context : SerializationContext) = throw UnsupportedOperationException()
    override fun rpcServerSerializerFactory(context : SerializationContext) = throw UnsupportedOperationException()
}