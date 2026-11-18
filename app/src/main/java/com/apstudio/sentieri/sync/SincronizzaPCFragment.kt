package com.apstudio.sentieri.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.apstudio.sentieri.NetworkUtils
import com.apstudio.sentieri.databinding.FragmentSincronizzaPcBinding
import com.apstudio.sentieri.db.SentieriDB
import com.apstudio.sentieri.db.SentieriRepo
import java.net.InetAddress
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "SincronizzaPCFragment"

class SincronizzaPCFragment : Fragment() {

    private var _binding: FragmentSincronizzaPcBinding? = null
    private val binding get() = _binding!!

    private var pcSyncServer: PcSyncServer? = null
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSincronizzaPcBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()
        val database = SentieriDB.getInstance(context.applicationContext)
        val repo = SentieriRepo(
            sentieriDao = database.sentieriDao(),
            poiDao = database.poiDao(),
            fotoPoiDao = database.fotoPoiDao(),
            trackDao = database.trackDao()
        )

        val ipAddress = getWifiIpAddress(context)
        val isWifi = NetworkUtils.isWifiConnected(context)

        if (!isWifi || ipAddress == null) {
            binding.tvWifiInfo.text = "Wi-Fi NON connesso! Connetti lo smartphone alla rete Wi-Fi locale."
            binding.tvStatus.text = "Sincronizzazione non disponibile senza Wi-Fi"
            binding.progressSync.visibility = View.GONE
            Toast.makeText(context, "Connetti lo smartphone al Wi-Fi per la sincronizzazione", Toast.LENGTH_LONG).show()
            appendLog("ERRORE: Rete Wi-Fi non rilevata.")
        } else {
            binding.tvWifiInfo.text = "Wi-Fi: Connesso | IP: http://$ipAddress:8080"
            binding.tvStatus.text = "In attesa di connessione dal PC..."
            binding.progressSync.visibility = View.VISIBLE
            appendLog("Rete Wi-Fi attiva. Indirizzo IP: $ipAddress")

            // 1. Avvia Server HTTP integrato
            pcSyncServer = PcSyncServer(
                context = context,
                port = 8080,
                repository = repo,
                onClientConnected = { clientInfo ->
                    activity?.runOnUiThread {
                        if (_binding != null) {
                            binding.tvStatus.text = clientInfo
                        }
                    }
                },
                onLogActivity = { logMsg ->
                    activity?.runOnUiThread {
                        if (_binding != null) {
                            appendLog(logMsg)
                        }
                    }
                }
            )
            pcSyncServer?.start()

            // 2. Avvia annuncio NSD (mDNS)
            startNsdService(context, 8080)
        }

        binding.btnStopSync.setOnClickListener {
            stopAllServices()
            findNavController().navigateUp()
        }
    }

    private fun startNsdService(context: Context, port: Int) {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("SentieriMulticastLock").apply {
                setReferenceCounted(true)
                acquire()
            }

            nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "SentieriServer"
                serviceType = "_sentieri-sync._tcp."
                setPort(port)
            }

            registrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(service: NsdServiceInfo) {
                    Log.d(TAG, "Servizio NSD registrato: ${service.serviceName}")
                    activity?.runOnUiThread {
                        if (_binding != null) {
                            appendLog("Servizio mDNS 'SentieriServer' registrato sulla rete.")
                        }
                    }
                }

                override fun onRegistrationFailed(service: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "Errore registrazione NSD: $errorCode")
                    activity?.runOnUiThread {
                        if (_binding != null) {
                            appendLog("Errore registrazione mDNS: codice $errorCode")
                        }
                    }
                }

                override fun onServiceUnregistered(service: NsdServiceInfo) {
                    Log.d(TAG, "Servizio NSD deregistrato")
                }

                override fun onUnregistrationFailed(service: NsdServiceInfo, errorCode: Int) {}
            }

            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Errore avvio NSD", e)
            appendLog("Errore avvio mDNS: ${e.localizedMessage}")
        }
    }

    private fun stopAllServices() {
        try {
            pcSyncServer?.stop()
            pcSyncServer = null

            registrationListener?.let {
                nsdManager?.unregisterService(it)
            }
            registrationListener = null

            multicastLock?.let {
                if (it.isHeld) it.release()
            }
            multicastLock = null
            appendLog("Servizi arrestati.")
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante lo stop dei servizi", e)
        }
    }

    private fun appendLog(message: String) {
        val now = timeFormat.format(Date())
        val currentText = binding.tvLogText.text.toString()
        val newLog = "[$now] $message\n$currentText"
        binding.tvLogText.text = newLog
    }

    private fun getWifiIpAddress(context: Context): String? {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                return String.format(
                    Locale.US, "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }

            // Fallback: scansiona interfacce di rete
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is InetAddress) {
                        val host = addr.hostAddress
                        if (host != null && !host.contains(":")) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore calcolo IP Wi-Fi", e)
        }
        return null
    }

    override fun onDestroyView() {
        stopAllServices()
        super.onDestroyView()
        _binding = null
    }
}
