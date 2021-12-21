package com.serenegiant.libcommon
/*
 * libcommon
 * utility/helper classes for myself
 *
 * Copyright (c) 2014-2021 saki t_saki@serenegiant.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
*/

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.serenegiant.dialog.RationalDialogV4
import com.serenegiant.libcommon.TitleFragment.OnListFragmentInteractionListener
import com.serenegiant.libcommon.list.DummyContent
import com.serenegiant.libcommon.list.DummyContent.DummyItem
import com.serenegiant.system.BuildCheck
import com.serenegiant.system.PermissionCheck
import com.serenegiant.system.PermissionUtils
import com.serenegiant.system.PermissionUtils.PermissionCallback
import com.serenegiant.widget.IPipelineView
import java.util.*

class MainActivity
	: AppCompatActivity(),
		OnListFragmentInteractionListener,
		RationalDialogV4.DialogResultListener {

	private var mPermissions: PermissionUtils? = null
	private var mIsResumed = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		// パーミッション要求の準備
		mPermissions = PermissionUtils(this, mCallback)
			.prepare(this,
				arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
						Manifest.permission.ACCESS_COARSE_LOCATION))
		DummyContent.createItems(this, R.array.list_items)
		if (savedInstanceState == null) {
			supportFragmentManager
				.beginTransaction()
				.replace(R.id.container, TitleFragment.newInstance(1))
				.commit()
		}
	}

	override fun onStart() {
		super.onStart()
		if (BuildCheck.isAndroid7()) {
			mIsResumed = true
			internalOnResume()
		}
	}

	override fun onResume() {
		super.onResume()
		if (!BuildCheck.isAndroid7()) {
			mIsResumed = true
			internalOnResume()
		}
	}

	override fun onPause() {
		if (!BuildCheck.isAndroid7()) {
			internalOnPause()
			mIsResumed = false
		}
		super.onPause()
	}

	override fun onStop() {
		if (BuildCheck.isAndroid7()) {
			internalOnPause()
			mIsResumed = false
		}
		super.onStop()
	}

	private fun internalOnResume() {
	}

	private fun internalOnPause() {
		clearToast()
	}

	/**
	 * helper method to get this Activity is paused or not
	 * @return true: this Activity is paused, false: resumed
	 */
	val isPaused: Boolean
		get() = !mIsResumed

	override fun onBackPressed() {
		if (DEBUG) Log.v(TAG, "onBackPressed:")
		// Fragment内の子Fragmentを切り替えた時にbackキーを押すと
		// Fragment自体がpopBackされてしまうのに対するworkaround
		val fm = supportFragmentManager
		val fragment = fm.findFragmentById(R.id.container)
		if (fragment is BaseFragment) {
			val childFm = fragment.getChildFragmentManager()
			if (childFm.backStackEntryCount > 0) { // HomeFragmentの子Fragmentがバックスタックに有る時はそれをpopBackする
				childFm.popBackStack()
				return
			}
			if (fragment.onBackPressed()) {
				return
			}
		}
		super.onBackPressed()
	}

	@SuppressLint("NewApi")
	override fun onListFragmentInteraction(item: DummyItem) {
		if (DEBUG) Log.v(TAG, "onListFragmentInteraction:$item")
		if (DEBUG) Log.v(TAG, "onListFragmentInteraction:enableVSync=${BuildConfig.ENABLE_VSYNC}")
		var fragment: Fragment? = null
		when (item.id) {
			0 -> {	// SAF
				if (BuildCheck.isLollipop()) {
					fragment = SAFUtilsFragment()
				} else {
					showToast(Toast.LENGTH_SHORT, "This feature is only available on API>=21")
				}
			}
			1 -> {	// SAFContentProvider
				if (BuildCheck.isLollipop()) {
					fragment = SAFFilerFragment()
				} else {
					showToast(Toast.LENGTH_SHORT, "This feature is only available on API>=21")
				}
			}
			2 -> {	// NetworkConnection
				if (!checkPermissionNetwork()) {
					return
				}
				fragment = NetworkConnectionFragment.newInstance()
			}
			3 -> {	// UsbMonitor
				if (BuildCheck.isAndroid9()
					&& !checkPermissionCamera()) {
					return
				}
				fragment = UsbFragment.newInstance()
			}
			4 -> {	// Camera
				if (!checkPermissionCamera()
					|| !checkPermissionWriteExternalStorage()
					|| !checkPermissionAudio()) {
					return
				}
				fragment = CameraFragment.newInstance(
					R.layout.fragment_camera, R.string.title_camera)
			}
			5 -> {	// Camera(MediaAVRecorder)
				if (!checkPermissionCamera()
					|| !checkPermissionWriteExternalStorage()
					|| !checkPermissionAudio()) {
					return
				}
				fragment = CameraRecFragment.newInstance(
					R.layout.fragment_camera, R.string.title_camera_rec)
			}
			6 -> {	// Camera(MediaAVRecorder+EncoderPipeline)
				if (!checkPermissionCamera()
					|| !checkPermissionWriteExternalStorage()
					|| !checkPermissionAudio()) {
					return
				}
				fragment = CameraRecFragment.newInstance(
					R.layout.fragment_simple_video_source_camera, R.string.title_camera_rec_pipeline,
					IPipelineView.EFFECT_PLUS_SURFACE,
					true // trueならEncoderPipelineを使った録画, falseならSurfaceEncoderを使った録画
				)
			}
			7 -> {	// Camera(FaceDetectPipeline)
				if (!checkPermissionCamera()
					|| !checkPermissionWriteExternalStorage()
					|| !checkPermissionAudio()) {
					return
				}
				fragment = CameraRecFragment.newInstance(
					R.layout.fragment_simple_video_source_camera, R.string.title_camera_face_detect,
					IPipelineView.PREVIEW_ONLY,
					enablePipelineEncode = false,
					enableFaceDetect = true
				)
			}
			8 -> {	// EffectCamera
				if (!checkPermissionCamera()
					|| !checkPermissionWriteExternalStorage()
					|| !checkPermissionAudio()) {
					return
				}
				fragment = EffectCameraFragment.newInstance()
			}
			9 -> {	// MixCamera
				if (!checkPermissionCamera()
					|| !checkPermissionWriteExternalStorage()
					|| !checkPermissionAudio()) {
					return
				}
				fragment = CameraFragment.newInstance(
					R.layout.fragment_camera_mix, R.string.title_mix_camera)
			}
			10 -> {	// OverlayCamera
				if (!checkPermissionCamera()
					|| !checkPermissionWriteExternalStorage()
					|| !checkPermissionAudio()) {
					return
				}
				fragment = CameraFragment.newInstance(
					R.layout.fragment_camera_overlay, R.string.title_overlay_camera)
			}
			11 -> {	// VideoSourceCamera
				if (!checkPermissionCamera()
					|| !checkPermissionWriteExternalStorage()
					|| !checkPermissionAudio()) {
					return
				}
				fragment = CameraFragment.newInstance(
					R.layout.fragment_camera_video_source, R.string.title_video_source_camera)
			}
			12 -> {	// VideoSourceDistributionCamera
				if (!checkPermissionCamera()
					|| !checkPermissionWriteExternalStorage()
					|| !checkPermissionAudio()) {
					return
				}
				fragment = CameraFragment.newInstance(
					R.layout.fragment_camera_distributor, R.string.title_video_source_dist_camera)
			}
			13 -> {	// ImageViewCamera
				if (!checkPermissionCamera()
					|| !checkPermissionWriteExternalStorage()
					|| !checkPermissionAudio()) {
					return
				}
				fragment = CameraFragment.newInstance(
					R.layout.fragment_camera_image_view, R.string.title_image_view_camera)
			}
			14 -> {	// TextureViewCamera
				if (!checkPermissionCamera()
					|| !checkPermissionWriteExternalStorage()
					|| !checkPermissionAudio()) {
					return
				}
				fragment = CameraFragment.newInstance(
					R.layout.fragment_camera_texture_view, R.string.title_texture_view_camera)
			}
			15 -> {	// SimpleCameraGL
				if (!checkPermissionCamera()
					|| !checkPermissionWriteExternalStorage()
					|| !checkPermissionAudio()) {
					return
				}
				fragment = CameraFragment.newInstance(
					R.layout.fragment_simple_camera_gl, R.string.title_simple_gl_camera)
			}
			16 -> {	// CameraSurface
				if (!checkPermissionCamera()
					|| !checkPermissionWriteExternalStorage()
					|| !checkPermissionAudio()) {
					return
				}
				fragment = CameraSurfaceFragment()
			}
			17 -> {	// SimpleVideoSourceCameraGLView
				if (!checkPermissionCamera()
					|| !checkPermissionWriteExternalStorage()
					|| !checkPermissionAudio()) {
					return
				}
				fragment = CameraFragment.newInstance(
					R.layout.fragment_simple_video_source_camera, R.string.title_simple_camera_source)
			}
			18 -> {	// DummyCameraGLView
				if (!checkPermissionCamera()
					|| !checkPermissionWriteExternalStorage()
					|| !checkPermissionAudio()) {
					return
				}
				fragment = CameraFragment.newInstance(
					R.layout.fragment_camera_dummy_source, R.string.title_dummy_camera_source)
			}
			19 -> {	// Galley
				if (!checkPermissionReadExternalStorage()) {
					return
				}
				fragment = GalleyFragment()
			}
			20 -> {	// Galley(RecyclerView,Cursor)
				if (!checkPermissionReadExternalStorage()) {
					return
				}
				fragment = GalleyFragment2()
			}
			21 -> {	// Galley(RecyclerView)
				if (!checkPermissionReadExternalStorage()) {
					return
				}
				fragment = GalleyFragment3()
			}
			22 -> {	// NumberKeyboard
				fragment = NumberKeyboardFragment()
			}
			23 -> {	// ViewSlider
				fragment = ViewSliderFragment()
			}
			24 -> {	// ProgressView
				fragment = ProgressFragment()
			}
			25 -> {	// PermissionUtils
				fragment = PermissionFragment.newInstance()
			}
			else -> {
			}
		}
		if (fragment != null) {
			supportFragmentManager
				.beginTransaction()
				.addToBackStack(null)
				.replace(R.id.container, fragment)
				.commit()
		}
	}

//================================================================================
	/**
	 * callback listener from RationalDialogV4
	 *
	 * @param dialog
	 * @param permissions
	 * @param result
	 */
	@SuppressLint("NewApi")
	override fun onDialogResult(
		dialog: RationalDialogV4,
		permissions: Array<String>, result: Boolean) {
		if (DEBUG) Log.v(TAG, "onDialogResult:result=${result}," + permissions.contentToString())
		if (result) { // メッセージダイアログでOKを押された時はパーミッション要求する
			if (BuildCheck.isMarshmallow()) {
				if (mPermissions != null) {
					mPermissions!!.requestPermission(permissions, false)
					return
				}
			}
		}
		// メッセージダイアログでキャンセルされた時とAndroid6でない時は自前でチェックして#checkPermissionResultを呼び出す
		for (permission in permissions) {
			checkPermissionResult(permission,
				PermissionCheck.hasPermission(this, permission))
		}
	}

	/**
	 * actual handling of requesting permission
	 * this method just show Toast if permission request failed
	 *
	 * @param permission
	 * @param result
	 */
	private fun checkPermissionResult(
		permission: String?, result: Boolean) {

		// パーミッションがないときにはメッセージを表示する
		if (!result && permission != null) {
			if (Manifest.permission.RECORD_AUDIO == permission) {
				showToast(Toast.LENGTH_SHORT, R.string.permission_audio)
			}
			if (Manifest.permission.WRITE_EXTERNAL_STORAGE == permission) {
				showToast(Toast.LENGTH_SHORT, R.string.permission_ext_storage)
			}
			if (Manifest.permission.CAMERA == permission) {
				showToast(Toast.LENGTH_SHORT, R.string.permission_camera)
			}
			if (Manifest.permission.INTERNET == permission) {
				showToast(Toast.LENGTH_SHORT, R.string.permission_network)
			}
			if (Manifest.permission.ACCESS_FINE_LOCATION == permission) {
				showToast(Toast.LENGTH_SHORT, R.string.permission_location)
			}
		}
	}

	/**
	 * check permission to access external storage
	 * and request to show detail dialog to request permission
	 *
	 * @return true already have permission to access external storage
	 */
	private fun checkPermissionWriteExternalStorage(): Boolean {
		// API29以降は対象範囲別ストレージ＆MediaStoreを使うのでWRITE_EXTERNAL_STORAGEパーミッションは不要
		return (BuildCheck.isAPI29()
			|| (mPermissions != null
				&& mPermissions!!.requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, true)))
	}

	/**
	 * check permission to read external storage
	 * and request to show detail dialog to request permission
	 *
	 * @return true already have permission to access external storage
	 */
	private fun checkPermissionReadExternalStorage(): Boolean {
		// WRITE_EXTERNAL_STORAGEがあればREAD_EXTERNAL_STORAGEはなくても大丈夫
		return PermissionCheck.hasWriteExternalStorage(this)
			|| (mPermissions != null
				&& mPermissions!!.requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE, true))
	}

	/**
	 * check permission to record audio
	 * and request to show detail dialog to request permission
	 *
	 * @return true already have permission to record audio
	 */
	private fun checkPermissionAudio(): Boolean {
		return mPermissions != null
			&& mPermissions!!.requestPermission(Manifest.permission.RECORD_AUDIO, true)
	}

	/**
	 * check permission to access internal camera
	 * and request to show detail dialog to request permission
	 *
	 * @return true already have permission to access internal camera
	 */
	private fun checkPermissionCamera(): Boolean {
		return mPermissions != null
			&& mPermissions!!.requestPermission(Manifest.permission.CAMERA, true)
	}

	/**
	 * check permission to access network
	 * and request to show detail dialog to request permission
	 *
	 * @return true already have permission to access network
	 */
	private fun checkPermissionNetwork(): Boolean {
		return mPermissions != null
			&& mPermissions!!.requestPermission(Manifest.permission.INTERNET, true)
	}

	/**
	 * check permission to access gps
	 * and request to show detail dialog to request permission
	 * @return true already have permission to access gps
	 */
	private fun checkPermissionLocation(): Boolean {
		return mPermissions != null
			&& mPermissions!!.requestPermission(LOCATION_PERMISSIONS, true)
	}

	/**
	 * PermissionUtilsからのコールバックリスナー実装
	 */
	private val mCallback: PermissionCallback = object : PermissionCallback {
		override fun onPermissionShowRational(permission: String) {
			if (DEBUG) Log.v(TAG, "onPermissionShowRational:$permission")
			// パーミッション要求理由の表示が必要な時
			val dialog: RationalDialogV4? =
				RationalDialogV4.showDialog(this@MainActivity, permission)
			if (dialog == null) {
				if (DEBUG) Log.v(TAG, "onPermissionShowRational:" +
					"デフォルトのダイアログ表示ができなかったので自前で表示しないといけない," + permission)
				if (Manifest.permission.INTERNET == permission) {
					RationalDialogV4.showDialog(this@MainActivity,
						R.string.permission_title,
						R.string.permission_network_request,
						arrayOf(Manifest.permission.INTERNET))
				} else if ((Manifest.permission.ACCESS_FINE_LOCATION == permission)
					|| (Manifest.permission.ACCESS_COARSE_LOCATION == permission)) {
					RationalDialogV4.showDialog(this@MainActivity,
						R.string.permission_title,
						R.string.permission_location_request,
						LOCATION_PERMISSIONS)
				}
			}
		}

		override fun onPermissionShowRational(permissions: Array<out String>) {
			if (DEBUG) Log.v(TAG, "onPermissionShowRational:" + permissions.contentToString())
			// 複数パーミッションの一括要求時はデフォルトのダイアログ表示がないので自前で実装する
			if (LOCATION_PERMISSIONS.contentEquals(permissions)) {
				RationalDialogV4.showDialog(this@MainActivity,
					R.string.permission_title,
					R.string.permission_location_request,
					LOCATION_PERMISSIONS)
			}
		}

		override fun onPermissionDenied(permission: String) {
			if (DEBUG) Log.v(TAG, "onPermissionDenied:$permission")
			// ユーザーがパーミッション要求を拒否したときの処理
		}

		override fun onPermission(permission: String) {
			if (DEBUG) Log.v(TAG, "onPermission:$permission")
			// ユーザーがパーミッション要求を承認したときの処理
		}

		override fun onPermissionNeverAskAgain(permission: String) {
			if (DEBUG) Log.v(TAG, "onPermissionNeverAskAgain:$permission")
			// 端末のアプリ設定画面を開くためのボタンを配置した画面へ遷移させる
			supportFragmentManager
				.beginTransaction()
				.addToBackStack(null)
				.replace(R.id.container, SettingsLinkFragment.newInstance())
				.commit()
		}

		override fun onPermissionNeverAskAgain(permissions: Array<out String>) {
			if (DEBUG) Log.v(TAG, "onPermissionNeverAskAgain:" + permissions.contentToString())
			// 端末のアプリ設定画面を開くためのボタンを配置した画面へ遷移させる
			supportFragmentManager
				.beginTransaction()
				.addToBackStack(null)
				.replace(R.id.container, SettingsLinkFragment.newInstance())
				.commit()
		}
	}

	//================================================================================
	private var mToast: Toast? = null

	/**
	 * Toastでメッセージを表示
	 * @param msg
	 * @param args
	 */
	private fun showToast(duration: Int, msg: String?, vararg args: Any?) {
		runOnUiThread {
			try {
				if (mToast != null) {
					mToast!!.cancel()
					mToast = null
				}
				val text = if (args != null) String.format(msg!!, *args) else msg!!
				mToast = Toast.makeText(this@MainActivity, text, duration)
				mToast!!.show()
			} catch (e: Exception) { // ignore
			}
		}
	}

	/**
	 * Toastでメッセージを表示
	 * @param msg
	 */
	private fun showToast(duration: Int, @StringRes msg: Int, vararg args: Any?) {
		runOnUiThread {
			try {
				if (mToast != null) {
					mToast!!.cancel()
					mToast = null
				}
				val text = args?.let { getString(msg, it) } ?: getString(msg)
				mToast = Toast.makeText(this@MainActivity, text, duration)
				mToast!!.show()
			} catch (e: Exception) {
				if (DEBUG) Log.d(TAG, "clearToast", e)
			}
		}
	}

	/**
	 * Toastが表示されていればキャンセルする
	 */
	private fun clearToast() {
		try {
			if (mToast != null) {
				mToast!!.cancel()
				mToast = null
			}
		} catch (e: Exception) {
			if (DEBUG) Log.d(TAG, "clearToast", e)
		}
	}

	companion object {
		private const val DEBUG = true // set false on production
		private val TAG = MainActivity::class.java.simpleName
		private val LOCATION_PERMISSIONS
			= arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
					  Manifest.permission.ACCESS_COARSE_LOCATION)
	}
}