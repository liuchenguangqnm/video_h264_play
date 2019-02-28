# video_h264_play
安卓硬解码播放h264文件    h264 file player by MediaCodec in Android

本Demo可兼容所有主流安卓手机和所有h264文件的播放和测试，基本没有明显的bug

注意事项：体验Demo前，请注意查看MainActivity上h264Path变量的值，将你想要播放的h264文件放置到对应的手机路径下

目前存在的问题：
1、魅族MX4 Pro 无法播放其中一个测试文件（3min_1080p.h264）
2、不知道如何借助硬解码的api获得不同h264文件的帧率，因此本demo在播放不同文h264文件的时候，部分文件可能会出现快进的情况
