package com.lcpatch

import android.content.Context
import android.os.Build
import android.util.AtomicFile
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.TimedPairingConnection
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Date
import java.util.concurrent.TimeUnit

internal object AdbIdentity {
    fun create(context: Context): AbsAdbConnectionManager {
        // App-private, excluded from backup. The pairing code is never persisted.
        val storage = AtomicFile(File(context.noBackupFilesDir, "wireless-adb-identity"))
        val identity: Pair<PrivateKey, Certificate> = if (storage.baseFile.exists()) {
            DataInputStream(storage.openRead()).use { input ->
                fun blob(): ByteArray {
                    val size = input.readInt()
                    require(size in 1..16384) { "ADB 身分資料損壞" }
                    return ByteArray(size).also { input.readFully(it) }
                }
                val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(blob()))
                val cert = CertificateFactory.getInstance("X.509").generateCertificate(blob().inputStream())
                key to cert
            }
        } else {
            val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val provider = BouncyCastleProvider()
            val name = X500Name("CN=LCPatch Wireless ADB")
            val now = System.currentTimeMillis()
            val certificate = JcaX509CertificateConverter().setProvider(provider).getCertificate(
                JcaX509v3CertificateBuilder(name, BigInteger(160, SecureRandom()), Date(now - 86400000),
                    Date(now + TimeUnit.DAYS.toMillis(3650)), name, keys.public)
                    .build(JcaContentSignerBuilder("SHA256withRSA").setProvider(provider).build(keys.private))
            )
            val output = storage.startWrite()
            try {
                val data = DataOutputStream(output)
                for (blob in listOf(keys.private.encoded, certificate.encoded)) {
                    data.writeInt(blob.size)
                    data.write(blob)
                }
                data.flush()
                storage.finishWrite(output)
            } catch (error: Exception) { storage.failWrite(output); throw error }
            keys.private to certificate
        }
        return object : AbsAdbConnectionManager() {
            override fun getPrivateKey(): PrivateKey = identity.first
            override fun getCertificate(): Certificate = identity.second
            override fun getDeviceName() = "LCPatch"
            override fun pair(host: String, port: Int, pairingCode: String): Boolean {
                require(host == "127.0.0.1")
                TimedPairingConnection(host, port, pairingCode.toByteArray(Charsets.UTF_8),
                    identity.first, identity.second, "LCPatch").use { it.start() }
                return true
            }
        }.apply {
            setApi(Build.VERSION.SDK_INT)
            setHostAddress("127.0.0.1")
            setTimeout(15, TimeUnit.SECONDS)
            setThrowOnUnauthorised(true)
        }
    }
}
