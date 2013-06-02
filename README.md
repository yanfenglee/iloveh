# iloveh

作为学习clojure的一个练习， 一个微信应用，微信号："iloveh--", 中文名： "我喜欢ta", 如果你喜欢一个人，
发送ta的微信号给我，如果ta也发送你的微信号给我，我会通知你们俩两情相悦。

## 微信使用方法
1. 关注微信号"iloveh--"
2. 根据微信提示信息，输入自己的微信号和email进行注册
3. 输入: @微信号 xxx，其中微信号为对方的微信号，xxx为你对ta的表白，输入"c"查询是否有人喜欢你，输入"h"请求帮助

## Installation

1. 安装mongodb
2. 安装leiningen2
3. 下载源码
4. 执行 lein uberjar

## Usage
上步编译后会生成一个单独的jar, 执行如下命令运行

    $ java -jar iloveh-0.1.0-standalone.jar


## License

Copyright © 2013 FIXME

Distributed under the Eclipse Public License, the same as Clojure.
