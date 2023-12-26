package icu.fur93.esp32ink

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat.checkSelfPermission
import com.google.android.material.snackbar.Snackbar
import icu.fur93.esp32ink.databinding.FragmentFirstBinding
import java.util.UUID

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */

val BLE_SERVICE_UUID = "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
val BLE_SERVICE_UPDATE_TIME_CHARACTERISTIC = "beb5483e-36e1-4688-b7f5-ea07361b26a8"

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    private var scanning = false;

    private lateinit var bluetoothLeScanner: BluetoothLeScanner

    private val deviceList = mutableListOf<BluetoothDevice>()
    private var currentDevice: BluetoothDevice? = null

    private lateinit var adapter: ArrayAdapter<String>
    private val uniqueAddresses = HashSet<String>()

    private var showDeviceAddress = false;

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, mutableListOf())

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        binding.spinnerDeviceList.adapter = adapter

        binding.spinnerDeviceList.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                @RequiresApi(Build.VERSION_CODES.R)
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    // 在选择变化时获取选中项的所有信息
                    val selectedDeviceInfo = deviceList[position]
                    if (checkSelfPermission(
                            requireContext(),
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Toast.makeText(requireContext(), "未授予蓝牙连接权限", Toast.LENGTH_SHORT)
                            .show()
                        return
                    }
                    Toast.makeText(
                        requireContext(),
                        "${selectedDeviceInfo.alias} - ${selectedDeviceInfo.name}\n地址: ${selectedDeviceInfo.address}",
                        Toast.LENGTH_SHORT
                    ).show()
                    currentDevice = selectedDeviceInfo
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // 未选择任何项时的处理
                }
            }

        binding.buttonGetDeviceList.setOnClickListener {
            if (scanning) {
                scanning = false;
                Snackbar.make(view, "停止扫描", Snackbar.LENGTH_SHORT).show()
                stopBluetoothScan()
                binding.buttonGetDeviceList.text = "刷新"
            } else {
                scanning = true;
                Snackbar.make(view, "开始扫描", Snackbar.LENGTH_SHORT).show()
                startBluetoothScan()
                binding.buttonGetDeviceList.text = "停止"
            }
        }

        binding.switchShowDeviceAddress.setOnClickListener {
            showDeviceAddress = binding.switchShowDeviceAddress.isChecked
            updateSpinnerAdapter()
        }

        binding.buttonUpdateTime.setOnClickListener {
            if (currentDevice == null) {
                Toast.makeText(requireContext(), "请先选择设备", Toast.LENGTH_SHORT).show()
            } else {
                connectToDevice(currentDevice!!)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    val scanCallback = object : ScanCallback() {
        @RequiresApi(Build.VERSION_CODES.R)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // 在这里处理扫描到的设备信息
            val device = result.device
            if (checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(requireContext(), "未授予蓝牙连接权限", Toast.LENGTH_SHORT)
                    .show()
                return
            };
            val deviceAlias = device.alias ?: "未知别称"
            val deviceName = device.name ?: "未知设备"
            val deviceAddress = device.address ?: "未知地址"
            if (uniqueAddresses.add(deviceAddress)) {
                Log.d("scanner", "device: $deviceAlias - $deviceName - $deviceAddress")

                // 添加设备信息到列表
                deviceList.add(device)

                // 更新 Spinner 的适配器
                updateSpinnerAdapter()
            }
        }
    }

    private fun startBluetoothScan() {
        // 在这里执行蓝牙扫描的相关操作
        // 例如：初始化蓝牙适配器，开始扫描周边蓝牙设备
        val bluetoothManager: BluetoothManager =
            requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
            bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
            if (checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(requireContext(), "未授予蓝牙扫描权限", Toast.LENGTH_SHORT)
                    .show()
                return
            }
            bluetoothLeScanner.startScan(scanCallback)
        } else {
            // 如果蓝牙未开启，您可以请求用户开启蓝牙
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, 1)
        }
    }

    private fun stopBluetoothScan() {
        if (checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(requireContext(), "未授予蓝牙连接权限", Toast.LENGTH_SHORT)
                .show()
            return
        }
        bluetoothLeScanner.stopScan(scanCallback)
    }

    private fun connectToDevice(device: BluetoothDevice) {
        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // 连接成功，发现服务
                    if (checkSelfPermission(
                            requireContext(),
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Toast.makeText(requireContext(), "未授予蓝牙连接权限", Toast.LENGTH_SHORT)
                            .show()
                        return
                    }
                    gatt?.discoverServices()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                // 服务已发现，找到目标服务并发送信息
                val targetService = gatt?.getService(UUID.fromString(BLE_SERVICE_UUID))
                val targetCharacteristic = targetService?.getCharacteristic(
                    UUID.fromString(
                        BLE_SERVICE_UPDATE_TIME_CHARACTERISTIC
                    )
                )
                // 发送数据
                writeDataToDevice(
                    gatt,
                    targetCharacteristic,
                    (System.currentTimeMillis() / 1000).toString().toByteArray()
                )

            }
        }

        device.connectGatt(requireContext(), false, gattCallback)
    }

    private fun writeDataToDevice(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        data: ByteArray
    ) {
        characteristic?.value = data
        if (checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(requireContext(), "未授予蓝牙连接权限", Toast.LENGTH_SHORT)
                .show()
            return
        }
        gatt?.writeCharacteristic(characteristic)
    }

    private fun updateSpinnerAdapter() {
        // 从设备信息列表中获取设备名称列表
        val deviceNames = deviceList.map {
            if (checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(requireContext(), "未授予蓝牙连接权限", Toast.LENGTH_SHORT)
                    .show()
                return
            }
            if (showDeviceAddress) it.address ?: "未知地址" else it.name ?: "未知设备"
        }

        // 清空适配器的数据
        adapter.clear()

        // 添加新数据到适配器
        adapter.addAll(deviceNames)

        // 通知适配器数据变化
        adapter.notifyDataSetChanged()
    }
}