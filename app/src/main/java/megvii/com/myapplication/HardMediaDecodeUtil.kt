package megvii.com.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.*
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import java.io.File
import java.io.FileInputStream

import java.lang.Exception
import java.nio.ByteBuffer
import java.util.*


/**
 * 日期 ---------- 维护人 ------------ 变更内容 --------
 * 2019/1/25        Sunshine          请填写变更内容
 */
class HardMediaDecodeUtil(h264Path: String, surfaceView: SurfaceView, isResume: Boolean) {
    // 解码对象的格式和展示画面的surfaceView
    val mediaFormatStr = MediaFormat.MIMETYPE_VIDEO_AVC
    val surfaceView = surfaceView
    // 解码对象
    private lateinit var mediaCodec: MediaCodec
    private lateinit var mediaFormat: MediaFormat
    // 解码缓存队列
    private val bufferInfo: MediaCodec.BufferInfo by lazy { MediaCodec.BufferInfo() }
    // 是否需要继续播放的标记
    private var isNeedContinuePlay: Boolean = false
    // 文件读取是否完成的标记
    private var isFileReadFinished: Boolean = true
    private val h264File = File(h264Path)
    // 是否重复播放
    private val isResume = isResume
    // h264数据段列表缓存
    private val h264SaveArr by lazy { Vector<ByteArray>() }
    // 解码所需线程列表缓存
    private var readFileThread: Thread? = null
    private var decodeThread: Thread? = null
    private var showFrameThread: Thread? = null
    // 实际展示出来的图像的宽高
    private var width = 0
    private var height = 0
    private var isFixingWH = true  // 宽高是否修正完成的标记
    // 播放间隔数据
    private var frameRateValue = 35
    private val changeSurfaceViewWhHandler by lazy<Handler> {
        // 视频宽高修正handler
        object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message?) {
                super.handleMessage(msg)
                if (msg?.obj != null) {
                    val layoutParams = msg.obj as ViewGroup.LayoutParams
                    surfaceView.layoutParams = layoutParams
                    postDelayed({
                        // 标记视频宽高修订已经完成了
                        isFixingWH = false
                        // TODO surfaceView已经显示第一帧画面后又重新修改surfaceView宽高导致解析发生异常，需要重新初始化所有播放线程并播放
                        ReplayThread().start()
                    }, 300) // 延迟300毫秒修改状态，确保视屏宽高修正完毕
                }
            }
        }
    }

    init {
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceChanged(p0: SurfaceHolder?, p1: Int, p2: Int, p3: Int) {

            }

            override fun surfaceDestroyed(p0: SurfaceHolder?) {
                resolveMediaDecoder()
            }

            override fun surfaceCreated(p0: SurfaceHolder?) {
                val permissionExternalR = ContextCompat.checkSelfPermission(
                    MyApplication.instance, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                val permissionExternalW = ContextCompat.checkSelfPermission(
                    MyApplication.instance, Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                if (permissionExternalR && permissionExternalW) {
                    initSurfaceConfig()
                }
            }
        })
        if (surfaceView.width != 0) { // 宽高可以直接获取，说明加载完成，可以播放
            val permissionExternalR = ContextCompat.checkSelfPermission(
                MyApplication.instance, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            val permissionExternalW = ContextCompat.checkSelfPermission(
                MyApplication.instance, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (permissionExternalR && permissionExternalW) {
                initSurfaceConfig()
            }
        }
    }

    private fun initSurfaceConfig() {
        try {
            // 根据视频编码创建解码器，这里是解码AVC编码的视频
            mediaCodec = MediaCodec.createDecoderByType(mediaFormatStr)
            // 初步设置展示视频的 宽，高
            mediaFormat = if (width > 0 && height > 0) {
                MediaFormat.createVideoFormat(mediaFormatStr, width, height)
            } else {
                MediaFormat.createVideoFormat(mediaFormatStr, surfaceView.width, surfaceView.height)
            }
            configDecoder(mediaFormat, surfaceView)
            // 在线程中获取解码结果并且解析播放
            startPlay()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startPlay() {
        isNeedContinuePlay = true  // 开始持续解码播放
        isFileReadFinished = false // 文件未读取完成
        readFileThread = ReadFileThread()
        (readFileThread as ReadFileThread).start()
        decodeThread = DecodeThread()
        (decodeThread as DecodeThread).start()
        showFrameThread = ShowFrameThread()
        (showFrameThread as ShowFrameThread).start()
    }

    private fun resolveMediaDecoder() {
        try {
            // 文件解码停止
            isNeedContinuePlay = false
            if (mediaCodec != null) {
                // 停止解码，此时可以再次调用configure()方法
                mediaCodec.stop()
                // 释放内存
                mediaCodec.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 读取文件流线程
     * kotlin非静态内部类一定要加上inner修饰，否则会默认它是静态内部类
     */
    private inner class ReadFileThread : Thread() {
        // h264数据分帧显示的相关变量
        private val h264Byte = ByteArray(100000)            // 原始数据流
        private var divFrameByte = ByteArray(200000)        // 分帧数据流，这里之所以要写的比h264Byte更长是为了保证每一帧画面的完整性
        private val HEAD_OFFSET = 512         // h264 每一帧头部数据（这里的头部数据就是指 0X00000 这些）的长度
        private var frameOffset: Int = 0      // divFrameByte 已经存储的帧画面数据长度

        override fun run() {
            try {
                synchronized(this) {
                    h264SaveArr.clear()        // 清空内部数据
                    // 读出第一份h264数据
                    val fs = FileInputStream(h264File)
                    var readLength = fs.read(h264Byte)
                    // 然后开始进行循环解析
                    while (readLength > 0 && isNeedContinuePlay) {
                        // 将读出的h264数据放入队列中
                        val saveByteArray = ByteArray(readLength)
                        System.arraycopy(h264Byte, 0, saveByteArray, 0, readLength)
                        // 将h264格式的字节流进行整理、分帧，然后交由MediaCodec解码，首先复制出整个帧的数据流供分帧解析
                        if (frameOffset + readLength < 200000) {
                            System.arraycopy(h264Byte, 0, divFrameByte, frameOffset, readLength)
                            frameOffset += readLength
                        } else {
                            frameOffset = 0
                            System.arraycopy(h264Byte, 0, divFrameByte, frameOffset, readLength)
                            frameOffset += readLength
                        }
                        // 找到divFrameByte中属于下一帧画面开头的index（此时，即将播放的这一帧的开头index为0）
                        var nextFrameStartIndex = findHead(divFrameByte, frameOffset, HEAD_OFFSET)
                        // TODO 如果画面一直存在下一帧，那我们就需要一直获取并区分出下一帧的数据
                        while (nextFrameStartIndex > 0 && isNeedContinuePlay) {
                            // TODO 如果数据一开头就存在帧画面头部的标记，那就可以开始存储第一帧了
                            if (checkHead(divFrameByte, 0)) {
                                val frameByteArray = ByteArray(nextFrameStartIndex)
                                System.arraycopy(divFrameByte, 0, frameByteArray, 0, nextFrameStartIndex)
                                h264SaveArr.add(frameByteArray)
                            }
                            // 第一帧解析逻辑走完了，就要搞一个临时数组,备份原有的divFrameByte，用于删除 divFrameByte 中第一帧的数据
                            val tempDivFrameByte = ByteArray(200000)
                            System.arraycopy(divFrameByte, 0, tempDivFrameByte, 0, divFrameByte.size)
                            // TODO divFrameByte已经备份了，可以开始在原有frameByte基础上单独摘出下一帧的数据并修改divFrameByte的实际值了
                            divFrameByte = ByteArray(200000)
                            // 更新帧画面数据的存储长度，删掉已经解析完成的数据长度
                            frameOffset -= nextFrameStartIndex
                            // 将未解析的下一帧画面的部分数据置顶存储于 divFrameByte 数组中（当然，这也有可能是完整的一帧哇）
                            System.arraycopy(tempDivFrameByte, nextFrameStartIndex, divFrameByte, 0, frameOffset)
                            // so~ 为了可以一次性播放完所有完整帧画面的数据，我们需要再次查找一下这个数组内部是不是还存在下下一帧画面的开头，如果存在，二话不说，while循环回去存储下一帧
                            nextFrameStartIndex = findHead(divFrameByte, frameOffset, HEAD_OFFSET)
                            System.gc() // 最后，尽可能多地清空已经不需要的缓存数据
                        }
                        // 读出下一轮h264文件数据
                        readLength = fs.read(h264Byte)
                    }
                    isFileReadFinished = true  // 文件已经读取完成
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isNeedContinuePlay = false
            }
        }
    }

    /**
     * 解码H264字节数组为可播放的帧数据并入列MediaCodec
     * kotlin非静态内部类一定要加上inner修饰，否则会默认它是静态内部类
     */
    private inner class DecodeThread : Thread() {
        override fun run() {
            try {
                // 然后开始进行循环解析
                synchronized(this) {
                    while (!isFileReadFinished || h264SaveArr.size > 0) {
                        if (h264SaveArr.size > 0) {
                            val frameByte = h264SaveArr[0]
                            h264SaveArr.remove(frameByte)
                            // 解析最靠前一帧的h264数据 nextFrameStartIndex 就是第一帧画面的数据长度
                            decodeH264(frameByte, frameByte.size)
                        }
                    }
                    System.gc()                 // 最后，尽可能多地清空已经不需要的缓存数据
                }
                if (isResume) {                 // 是否重播
                    if (isNeedContinuePlay) {
                        ReplayThread().start()
                    }
                } else {
                    isNeedContinuePlay = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isNeedContinuePlay = false
            }
        }
    }

    /**
     * 从MediaCodec队列中取出帧数据用于SurfaceView的展示
     */
    private inner class ShowFrameThread : Thread() {
        override fun run() {
            try {
                synchronized(this) {
                    showFrameData()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isNeedContinuePlay = false
            }
        }
    }

    /**
     * 重新播放的线程
     */
    private inner class ReplayThread : Thread() {
        override fun run() {
            isNeedContinuePlay = false
            while (readFileThread?.isAlive!! || decodeThread?.isAlive!! || showFrameThread?.isAlive!!) {
                // 确保所有不需要的现存线程结束
            }
            // 重新播放
            startPlay()
        }
    }

    /**
     * h264帧数据解码
     */
    private fun decodeH264(input: ByteArray, decodeLength: Int) {
        // 获取MediaCodec的输入流
        val inputBuffers = mediaCodec.inputBuffers
        // 反序列化入列流，通过参数可以设置解码等待时间，0为不等待，-1为一直等待，其余为毫秒级时间长度
        val inputBufferIndex = mediaCodec.dequeueInputBuffer(-1) // 从这里拿到哪一个index所属的InputBuffer是可用的
        //填充数据到输入流
        if (inputBufferIndex >= 0) {
            val byteBuffer: ByteBuffer = inputBuffers[inputBufferIndex] // 获取到指定index的byteBuffer
            byteBuffer.clear() // 清理上一次放入的数据
            byteBuffer.put(input, 0, decodeLength)
            // 序列化入列流（最后一个参数对应flag，不知道是干什么的）
            mediaCodec.queueInputBuffer(inputBufferIndex, 0, decodeLength, System.currentTimeMillis(), 0)
        }
    }

    /**
     * h264帧数据播放
     */
    private fun showFrameData() {
        while (isNeedContinuePlay) {
            // MediaCodec帧数据出列，先将帧数据信息放入已经准备好的MediaCodec.BufferInfo对象中（参数2为延迟时间，设置为10秒（播放本地文件的延迟时间））
            var outputBufferIndex =
                mediaCodec.dequeueOutputBuffer(bufferInfo, -1)    // 从这里拿到哪一个index所属的outputBuffer是可用的，负数就是没有
            Log.i("haha", "$outputBufferIndex")
            if (outputBufferIndex >= 0) {
                // 产生有效数据，调节surfaceView宽高
                if (isFixingWH) {
                    fixHW(mediaFormatStr, surfaceView)
                }
                // 释放指定index的帧数据，让SurfaceView播放，参数2为render，即是否渲染这一帧的数据，写false就是不提交SurfaceView播放就释放这一帧的数据
                mediaCodec.releaseOutputBuffer(outputBufferIndex, true)
                Thread.sleep(frameRateValue.toLong())
            }
        }
    }

    /**
     * 找到 h364 数据流中下一帧画面开头的index
     *
     * @param buffer
     * @param len
     * @return the offset of frame head, return 0 if can not find one
     */
    internal fun findHead(buffer: ByteArray, len: Int, headOffset: Int): Int {
        var i = headOffset
        while (i < len) {
            if (checkHead(buffer, i))
                break
            i++
        }
        if (i == len)
            return 0
        return if (i == headOffset) 0 else i
    }

    /**
     * 查找 h264 数据流当前index数据是否是一帧画面的开头
     *
     * @param buffer
     * @param offset
     * @return whether the src buffer is frame head
     */
    internal fun checkHead(buffer: ByteArray, offset: Int): Boolean {
        // 00 00 00 01
        if (buffer[offset].toInt() == 0 && buffer[offset + 1].toInt() == 0
            && buffer[offset + 2].toInt() == 0 && buffer[3].toInt() == 1
        ) return true
        // 00 00 01
        if (buffer[offset].toInt() == 0 && buffer[offset + 1].toInt() == 0
            && buffer[offset + 2].toInt() == 1
        ) return true
        // 其它情况
        return false
    }

    /**
     * 对mediaFormat配置相应的surfaceView
     */
    private fun configDecoder(newMediaFormat: MediaFormat, surfaceView: SurfaceView) {
        if (mediaCodec != null) {
            // 在SurfaceView加载完成前，调用以下方法会报错，此处TryCatch用以应付在OnCreate中执行初始化导致的崩溃
            try {
                mediaCodec.stop()
                mediaFormat = newMediaFormat
                // MediaCodec配置对应的SurfaceView
                // ！！！注意，这行代码需要SurfaceView界面绘制完成之后才可以调用！！！
                mediaCodec.configure(newMediaFormat, surfaceView.holder.surface, null, 0)
                // 解码模式设置
                // mediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR) // 表示编码器会尽量把输出码率控制为设定值
                mediaFormat.setInteger(
                    MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ
                )  // 示完全不控制码率，尽最大可能保证图像质量
                // mediaFormat.setInteger( MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR) // 表示编码器会根据图像内容的复杂度（实际上是帧间变化量的大小）来动态调整输出码率，图像复杂则码率高，图像简单则码率低
                mediaCodec.start()
                // 设置视频保持纵横比，此方法必须在configure和start之后执行才有效
                mediaCodec.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
        }
    }


    /**
     * ================================================== 修正视频宽高相关逻辑 ==================================================
     */
    // 获取视频宽高以缩放渲染分辨率
    private fun fixHW(mediaFormatStr: String, surfaceView: SurfaceView) {
        var sourceWidth = 0
        var sourceHeight = 0
        // 获取MatchParent状态下，surfaceView的宽和高
        width = surfaceView.width
        height = surfaceView.height
        // 缓存原surface的宽高
        sourceWidth = width
        sourceHeight = height
        try {
            // TODO 获取视频格式信息，注意此方法必须在releaseOutputBuffer之后执行一次到多次方可获得真实的视频宽高
            val currentFormat = mediaCodec.outputFormat
            var mediaW = currentFormat.getInteger("width")
            val mediaH = currentFormat.getInteger("height")
            if (mediaW == width && mediaH == height) // 宽高未变，不继续下一步
                return
            if (mediaW > mediaH) { // 画面横屏，以宽度为基准计算
                // 计算高宽比
                val heightChangeRate = mediaH * 1.0f / mediaW
                // 得到应该的高度并赋值
                height = (width * heightChangeRate + .5f).toInt()
                if (height > sourceHeight) { // 高度超屏幕，那就以原高度为基准进行宽度修正
                    height = sourceHeight
                    // 计算宽高比
                    val widthChangeRate = mediaW * 1.0f / mediaH
                    // 得到应该的宽度并赋值
                    width = (sourceHeight * widthChangeRate + .5f).toInt()
                }
            } else { // 画面竖屏，以高度为基准计算
                // 计算宽高比
                val widthChangeRate = mediaW * 1.0f / mediaH
                // 得到应该的宽度并赋值
                width = (height * widthChangeRate + .5f).toInt()
                if (width > sourceWidth) { // 宽度超屏幕，那就以原宽度为基准进行高度修正
                    width = sourceWidth
                    // 计算高宽比
                    val heightChangeRate = mediaH * 1.0f / mediaW
                    // 得到应该的高度并赋值
                    height = (sourceWidth * heightChangeRate + .5f).toInt()
                }
            }
            // 正式修正surfaceView的宽高
            doFix(mediaFormatStr, surfaceView)
        } catch (e: IllegalStateException) {
            width = 0
            height = 0
            e.printStackTrace()
        }
    }

    private fun doFix(mediaFormatStr: String, surfaceView: SurfaceView) {
        // 应用新的渲染分辨率
        val newMediaFormat = MediaFormat.createVideoFormat(mediaFormatStr, width, height)
        // 杀心MediaFormat
        configDecoder(newMediaFormat, surfaceView)
        // 运算videoSurfaceView的宽高以保持纵横比
        val surfaceLayoutParams = surfaceView.layoutParams
        // 将实际应该有的宽高赋值给LayoutParams
        surfaceLayoutParams.width = width
        surfaceLayoutParams.height = height
        fixVideoLayout(surfaceLayoutParams)
    }

    // 应用videoSurfaceView的宽高，由于解码线程在子线程，这里需要切回主线程执行
    private fun fixVideoLayout(surfaceLayoutParams: ViewGroup.LayoutParams) {
        val msg = Message()
        msg.obj = surfaceLayoutParams
        changeSurfaceViewWhHandler.sendMessage(msg)
    }
}
