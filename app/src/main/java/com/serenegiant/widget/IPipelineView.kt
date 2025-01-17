package com.serenegiant.widget
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

import com.serenegiant.glpipeline.IPipeline
import com.serenegiant.glutils.GLManager

/**
 * IPipelineSource/IPipelineで描画処理分配処理を行うViewの共通メソッドを定義するインターフェース
 */
interface IPipelineView {
	/**
	 * 指定したIPipelineオブジェクトをパイプラインチェーンに追加
	 * (削除するときはIPipeline#removeを使うこと)
	 * @param pipeline
	 */
	fun addPipeline(pipeline: IPipeline)

	/**
	 * IPipeline/IPipelineSourceの処理に使うGLManagerインスタンスを取得する
	 */
	fun getGLManager(): GLManager

	companion object {
		const val PREVIEW_ONLY = 0
		const val EFFECT_ONLY = 1
		const val EFFECT_PLUS_SURFACE = 2
	}

}