# Kurento With Screen Share

English:

Before run the project, you need to installed a Chrome plugin that name is ` Screen Capturing `.
And configure the HTTPS proxy service, like nginx.
And install your Kurento media server, change the url connection to your Kurento media server
in `src/main/java/signaling`. Like this:

    private KurentoClient kurento = KurentoClient.create("ws://192.168.1.180:8888/kurento");

中文：

运行该项目前，需要先在谷歌浏览器中安装由`www.webrtc-experiment.com`提供的`Screen Capturing`插件，
以及配置相应的HTTPS服务，如Nginx。
以及安装好你自己的Kurento media server，并将链接改为连接到你的KMS的地址。就像下面这行代码：

    private KurentoClient kurento = KurentoClient.create("ws://192.168.1.180:8888/kurento");
