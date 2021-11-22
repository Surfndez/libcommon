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

import com.serenegiant.utils.Pool;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * IMediaQueueのオンメモリー実装
 * LinkedBlockingQueueを使用
 */
public class MemMediaQueue implements IMediaQueue {
	@NonNull
	private final LinkedBlockingQueue<IRecycleBuffer> mQueue
		= new LinkedBlockingQueue<IRecycleBuffer>();
	@NonNull
	private final IRecycleBuffer.Factory mFactory;
	@NonNull
	private final Pool<IRecycleBuffer> mPool;
	
	/**
	 * MemMediaQueue用のデフォルトファクトリークラス
	 */
	public static class DefaultFactory implements IRecycleBuffer.Factory {
		@NonNull
		@Override
		public IRecycleBuffer create(@NonNull final IRecycleParent parent,
			@Nullable final Object... args) {

			return new RecycleMediaData(parent);
		}
	}

	/**
	 * コンストラクタ
	 * DefaultFactoryをファクトリーとして使う
 	 * @param initNum
	 * @param maxNumInPool
	 */
	public MemMediaQueue(final int initNum, final int maxNumInPool) {
		this(initNum, maxNumInPool, null);
	}

	/**
	 * コンストラクタ
	 * @param initNum
	 * @param maxNumInPool
	 * @param factory
	 */
	public MemMediaQueue(final int initNum, final int maxNumInPool,
		@Nullable final IRecycleBuffer.Factory factory) {

		mFactory = factory != null ? factory : new DefaultFactory();
		mPool = new Pool<IRecycleBuffer>(initNum, maxNumInPool) {
			@NonNull
			@Override
			protected IRecycleBuffer createObject(
				@Nullable final Object... args) {

				return mFactory.create(MemMediaQueue.this, args);
			}
		};
	}
	
	@Override
	public void clear() {
		mQueue.clear();
		mPool.clear();
	}
	
	@Override
	public IRecycleBuffer obtain(@Nullable final Object... args) {
		return mPool.obtain(args);
	}
	
	@Override
	public boolean queueFrame(@NonNull final IRecycleBuffer buffer) {
		return mQueue.offer(buffer);
	}
	
	@Override
	@Nullable
	public IRecycleBuffer peek() {
		return mQueue.peek();
	}
	
	@Override
	@Nullable
	public IRecycleBuffer poll() {
		return mQueue.poll();
	}
	
	@Override
	@Nullable
	public IRecycleBuffer poll(final long timeout, final TimeUnit unit)
		throws InterruptedException {

		return mQueue.poll(timeout, unit);
	}
	
	@Override
	public int count() {
		return mQueue.size();
	}
	
	@Override
	public boolean recycle(@NonNull final IRecycleBuffer buffer) {
		return mPool.recycle(buffer);
	}

}
