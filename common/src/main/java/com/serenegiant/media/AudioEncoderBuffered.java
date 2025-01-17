package com.serenegiant.media;
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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 *　FIFOキューによるバッファリング付きのAudioEncoder
 */
public class AudioEncoderBuffered extends AbstractAudioEncoder {
//	private static final boolean DEBUG = false;	// FIXME 実働時にはfalseにすること
	private static final String TAG = AudioEncoderBuffered.class.getSimpleName();

	private static final int MAX_POOL_SIZE = 100;
	private static final int MAX_QUEUE_SIZE = 100;

	private AudioThread mAudioThread = null;
	private DequeueThread mDequeueThread = null;
	/**
	 * キューに入れる音声データのバッファサイズ
	 */
	protected final int mBufferSize = SAMPLES_PER_FRAME;
	@NonNull
	private final MemMediaQueue mAudioQueue;

	public AudioEncoderBuffered(
		@NonNull final IRecorder recorder,
		@NonNull final EncoderListener listener,
		final int audio_source, final int audio_channels) {

		super(recorder, listener, audio_source, audio_channels, DEFAULT_SAMPLE_RATE, DEFAULT_BIT_RATE);
//		if (DEBUG) Log.v(TAG, "コンストラクタ:");
		if (audio_source < MediaRecorder.AudioSource.DEFAULT
			|| audio_source > MediaRecorder.AudioSource.VOICE_COMMUNICATION)
			throw new IllegalArgumentException("invalid audio source:" + audio_source);
		mAudioQueue = new MemMediaQueue(MAX_POOL_SIZE, MAX_POOL_SIZE, MAX_QUEUE_SIZE);
	}

	@Override
	public void start() {
		super.start();
		if (mAudioThread == null) {
			// 内蔵マイクからの音声取り込みスレッド生成＆実行
	        mAudioThread = new AudioThread();
			mAudioThread.start();
			mDequeueThread = new DequeueThread();
			mDequeueThread.start();
		}
	}

	@Override
	public void stop() {
		mAudioThread = null;
		mDequeueThread = null;
		super.stop();
	}

	/**
	 * AudioRecordから無圧縮PCM16bitで内蔵マイクからの音を取得してキューへ追加するためのスレッド
	 */
    private final class AudioThread extends Thread {
    	public AudioThread() {
    		super("AudioThread");
    	}

    	@Override
    	public final void run() {
    		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO); // THREAD_PRIORITY_URGENT_AUDIO
			final int buffer_size = AudioSampler.getAudioBufferSize(mChannelCount, mSampleRate,
				AbstractAudioEncoder.SAMPLES_PER_FRAME, AbstractAudioEncoder.FRAMES_PER_BUFFER);
/*
			final Class audioSystemClass = Class.forName("android.media.AudioSystem");
			// will disable the headphone
			setDeviceConnectionState.Invoke(audioSystemClass, (Integer)DEVICE_OUT_WIRED_HEADPHONE, (Integer)DEVICE_STATE_UNAVAILABLE, new String(""));
			// will enable the headphone
			setDeviceConnectionState.Invoke(audioSystemClass, (Integer)DEVICE_OUT_WIRED_HEADPHONE, (Integer)DEVICE_STATE_AVAILABLE, new Lang.String(""));
*/
    		final AudioRecord audioRecord = IAudioSampler.createAudioRecord(
    			mAudioSource, mSampleRate, mChannelCount, AudioFormat.ENCODING_PCM_16BIT, buffer_size);
			int err_count = 0;
            if (audioRecord != null) {
	            try {
	            	if (mIsCapturing) {
//						if (DEBUG) Log.v(TAG, "AudioThread:start audio recording");
		                int readBytes;
		                ByteBuffer buffer;
		                audioRecord.startRecording();
		                try {
		                	RecycleMediaData data;
		                	for ( ; ; ) {
		                		if (!mIsCapturing || mRequestStop) break;
								// check recording state
								final int recordingState = audioRecord.getRecordingState();
								if (recordingState != AudioRecord.RECORDSTATE_RECORDING) {
									if (err_count == 0) {
										Log.e(TAG, "not a recording state," + recordingState);
									}
									err_count++;
									if (err_count > 20) {
										break;
									} else {
										synchronized (mSync) {
											mSync.wait(100);
										}
										continue;
									}
								}
		                		data = mAudioQueue.obtain(mBufferSize);
		                		buffer = data.get();
		                		buffer.clear();
		                		try {
		                			readBytes = audioRecord.read(buffer, SAMPLES_PER_FRAME);
		                		} catch (final Exception e) {
//		    		        		Log.w(TAG, "AudioRecord#read failed:", e);
		                			break;
		                		}
								if (readBytes > 0) {
									// 内蔵マイクからの音声入力をエンコーダーにセット
									err_count = 0;
									// FIXME ここはMediaDataのセッターで一括でセットするように変更する
									data.presentationTimeUs(getInputPTSUs())
										.size(readBytes);
									buffer.position(readBytes);
									buffer.flip();
									mAudioQueue.queueFrame(data);
									continue;
								} else if (readBytes == AudioRecord.SUCCESS) {	// == 0
									err_count = 0;
									data.recycle();
									continue;
								} else if (readBytes == AudioRecord.ERROR) {
									if (err_count == 0) {
										Log.e(TAG, "Read error ERROR");
									}
								} else if (readBytes == AudioRecord.ERROR_BAD_VALUE) {
									if (err_count == 0) {
										Log.e(TAG, "Read error ERROR_BAD_VALUE");
									}
								} else if (readBytes == AudioRecord.ERROR_INVALID_OPERATION) {
									if (err_count == 0) {
										Log.e(TAG, "Read error ERROR_INVALID_OPERATION");
									}
								} else if (readBytes == AudioRecord.ERROR_DEAD_OBJECT) {
									Log.e(TAG, "Read error ERROR_DEAD_OBJECT");
									data.recycle();
									// FIXME AudioRecordを再生成しないといけない
									break;
								} else if (readBytes < 0) {
									if (err_count == 0) {
										Log.e(TAG, "Read returned unknown err " + readBytes);
									}
								}
								err_count++;
								data.recycle();
				    			if (err_count > 10) {
				    				break;
								}
		                	}	// end of for ( ; ; )
		                } finally {
		                	audioRecord.stop();
		                }
	            	}	// if (mIsCapturing)
	            } catch (final Exception e) {
//	        		Log.e(TAG, "exception on AudioRecord:", e);
	            } finally {
	            	audioRecord.release();
	            }
//	    	} else {
//        		Log.w(TAG, "AudioRecord failed to initialize");
	    	}	// if (audioRecord != null)
//			if (DEBUG) Log.v(TAG, "AudioThread:finished");
    	}
    }

    /**
     * キューから音声データを取り出してエンコーダーへ書き込むスレッド
     */
    private final class DequeueThread extends Thread {
    	public DequeueThread() {
    		super("DequeueThread");
    	}

    	@Override
    	public final void run() {
			RecycleMediaData data;
			int frame_count = 0;
    		for (; ;) {
        		synchronized (mSync) {
            		if (!mIsCapturing || mRequestStop) break;
            	}
    			try {
					data = mAudioQueue.poll(30, TimeUnit.MILLISECONDS);
				} catch (final InterruptedException e1) {
					break;
				}
    			if (data != null) {
    				if (data.size() > 0) {
    					encode(data.get(), data.size(), data.presentationTimeUs());
    					frameAvailableSoon();
    					frame_count++;
    				}
					data.recycle();
    			}
    		} // for
			if (frame_count == 0) {
		    	// 1フレームも書き込めなかった時は動画出力時にMediaMuxerがクラッシュしないように
		    	// ダミーデータを書き込む
		    	final ByteBuffer buf = ByteBuffer.allocateDirect(mBufferSize).order(ByteOrder.nativeOrder());
		    	for (int i = 0; mIsCapturing && (i < 5); i++) {
					buf.position(SAMPLES_PER_FRAME);
					buf.flip();
					encode(buf, SAMPLES_PER_FRAME, getInputPTSUs());
					frameAvailableSoon();
					synchronized (this) {
						try {
							wait(50);
						} catch (final InterruptedException e) {
							// ignore
						}
					}
		    	}
			}
    	}
    }

}
