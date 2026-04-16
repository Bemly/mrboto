# mruby 社区库 Android 兼容性分析

> 来源: https://mruby.org/libraries/ (321 个社区库)
> 分析日期: 2026-04-16

## 分类说明

- **可用** (165 个): 纯 Ruby 或 C 扩展无外部依赖，可直接嵌入 Android
- **需交叉编译外部 C 库** (24 个): 需要额外编译 libcurl/libssl/sqlite3 等
- **不适用** (131 个): 依赖桌面 GUI、Linux 特定 API、硬件外设等

---

## 可用 (165 个)

| 库名 | 说明 |
|------|------|
| gettextpo | GNU gettext PO 解析库 (需要 libgettextpo) |
| mruby-alarm | 闹钟模块 |
| mruby-allocate | Class allocate |
| mruby-ansi-colors | ANSI 颜色转义码 |
| mruby-apr | 跨平台标准库组件 |
| mruby-argtable | 命令行参数解析 |
| mruby-at_exit | Kernel#at_exit 方法 |
| mruby-avl | AVL 树实现 |
| mruby-aws-s3 | AWS S3 REST API 客户端 |
| mruby-aws-sigv4 | AWS Signature V4 签名库 |
| mruby-b64 | Base64 编解码 (流式接口) |
| mruby-backtrace | 显示 backtrace |
| mruby-base32 | Base32 编解码 |
| mruby-base58 | Base58 编解码 |
| mruby-base64 | Base64 编解码 |
| mruby-bignum | 自包含大整数实现 |
| mruby-bin-mirb-hostbased | 串口连接的 mirb |
| mruby-bin-monolith | 打包 mruby 为单文件可执行程序 |
| mruby-bin-mruby-afl | AFL fuzz 模式的 mruby 解释器 |
| mruby-c-ext-helpers | C 扩展辅助工具 |
| mruby-capacity | mruby 容量接口 |
| mruby-catch-throw | catch/throw 控制流 |
| mruby-cbor | CBOR (RFC 8949) 实现 |
| mruby-cfunc | 基于 libffi 的 C 函数接口 |
| mruby-changefinder | 变化点检测 |
| mruby-chrono | 稳定时钟和系统时钟 |
| mruby-class-attribute | 可继承类属性 |
| mruby-config | Ruby/C 双侧配置值维护 |
| mruby-correlation | 相关系数计算 |
| mruby-crc | 通用 CRC 计算器 |
| mruby-delegate | Delegate 模块 |
| mruby-digest | MD5/RMD160/SHA1/SHA256/SHA384/SHA512/HMAC 摘要 |
| mruby-dir | Dir 类 |
| mruby-dir-glob | File.fnmatch() & Dir.glob() |
| mruby-discount | Markdown 转 HTML (使用 discount) |
| mruby-eject | 弹出光驱 |
| mruby-env | ENV 类实现 |
| mruby-erb | ERB 模板引擎 |
| mruby-errno | Errno 模块 |
| mruby-eventfd | eventfd 类 |
| mruby-factory | Factory 模式实现 |
| mruby-fast-json | 快速 JSON 解析 |
| mruby-fast-remote-check | 高速端口监听检测 |
| mruby-file-access | 文件访问类 |
| mruby-file-fnmatch | File.fnmatch() |
| mruby-file-stat | File::Stat 类 |
| mruby-filemagic | 文件类型检测 (libmagic) |
| mruby-float4 | 小型向量类 |
| mruby-forwardable | Forwardable 模块 |
| mruby-fsm | 有限状态机 |
| mruby-gemcut | 运行时 gem 重配置器 |
| mruby-getoptlong | GNU getopt 长选项 |
| mruby-getopts | GNU getopt |
| mruby-getpass | 命令行密码读取 |
| mruby-gettimeofday | gettimeofday(2) 封装 |
| mruby-girffi-docgen | GIR FFI 文档生成 |
| mruby-gmp-bignum | GMP 大整数实现 |
| mruby-gntp | Growl 通知协议 |
| mruby-hashie | 增强 Hash 类 |
| mruby-hmac | HMAC 摘要 |
| mruby-hs-regexp | 轻量正则 (Henry Spencer) |
| mruby-http | HTTP 解析器 |
| mruby-httprequest | HTTP 请求类 |
| mruby-iijson | JSON 解析器 |
| mruby-implerr | ImplementationError 类 |
| mruby-io-copy_stream | IO.copy_stream 方法 |
| mruby-ipaddr | IP 地址类 |
| mruby-ipfilter | IP 过滤 |
| mruby-jpeg | JPEG 库 |
| mruby-json | JSON 处理 |
| mruby-jwt | JSON Web Token |
| mruby-kmp | KMP 搜索算法 |
| mruby-knn-detector | K 近邻异常检测 |
| mruby-limits | limits.h 常量 |
| mruby-linenoise | 行编辑库 (类似 readline) |
| mruby-logger | 日志工具 |
| mruby-markdown | Markdown 库 |
| mruby-marshal | Marshal 模块 |
| mruby-marshal-c | Marshal 模块 (C 实现) |
| mruby-marshal-fast | Marshal 模块 (C 快速实现) |
| mruby-matrix | 矩阵和向量库 |
| mruby-md5 | MD5 哈希 |
| mruby-method | Method/UnboundMethod 类 |
| mruby-mrmagick | ImageMagick 绑定 |
| mruby-msd | MSD 类 |
| mruby-msgpack | MessagePack 序列化 |
| mruby-mtest | 最小测试框架 |
| mruby-murmurhash1 | MurmurHash1 摘要 |
| mruby-murmurhash2 | MurmurHash2 摘要 |
| mruby-named-constants | 动态命名常量 |
| mruby-oauth | OAuth 类 |
| mruby-onig-regexp | Onigmo 正则表达式 (C 实现) |
| mruby-open3 | Open3 (带 stderr 的 popen) |
| mruby-optparse | OptionParser 命令行解析 |
| mruby-opvault | 解密 1Password vault |
| mruby-os | 系统和二进制能力检测 |
| mruby-ostruct | OpenStruct |
| mruby-otp | OTP (HOTP/TOTP) 生成和验证 |
| mruby-otpauth | 一次性密码类 |
| mruby-pcre-regexp | PCRE 正则表达式 |
| mruby-perlin-noise | Perlin 噪声生成器 |
| mruby-phr | picohttpparser 绑定 |
| mruby-ping | ICMP/ARP ping |
| mruby-pjson | 纯 Ruby JSON 解析器 |
| mruby-pkcs5 | PKCS5 功能 (需 mruby-digest) |
| mruby-poll | 系统 poll |
| mruby-posix-regexp | POSIX 正则 (libc) |
| mruby-posix_ipc | POSIX IPC API |
| mruby-proc-irep-ext | Proc IREP 扩展 |
| mruby-publicsuffix | 公共后缀域名解析 |
| mruby-pure-regexp | 纯 Ruby 正则表达式 |
| mruby-qrcode | QR 码生成 |
| mruby-r3 | libr3 路由分发绑定 |
| mruby-rake | Rake 构建工具 |
| mruby-random | Random 类 (Mersenne Twister) |
| mruby-regexp-pcre | PCRE 正则模块 |
| mruby-require | require 实现 |
| mruby-ripemd | RIPEMD-160 哈希 |
| mruby-romajify | 日语罗马字转换 |
| mruby-rubyffi-compat | RubyFFI 兼容层 |
| mruby-secure-compare | 安全字符串比较 |
| mruby-secure-random | SecureRandom 类 |
| mruby-set | Set 类 |
| mruby-sha1 | SHA1 哈希 |
| mruby-sha2 | SHA2 哈希 |
| mruby-shellwords | Shell 字符串处理 |
| mruby-signal | 信号捕获 |
| mruby-signal-thread | 多线程信号捕获 |
| mruby-simple-random | Kernel#rand / Kernel#srand |
| mruby-simplehttp | 简单 HTTP 客户端 |
| mruby-simplehttp-socket | 简单 HTTP 客户端 (socket) |
| mruby-simplehttpserver | 简单 HTTP 服务器 |
| mruby-simplemsgpack | 简单 MessagePack 封装 |
| mruby-simpletest | 简单测试框架 |
| mruby-singleton | Singleton 模块 |
| mruby-siphash | SipHash 消息摘要 |
| mruby-smallhttp | 小型 HTTP 客户端 |
| mruby-string-crypt | String#crypt 实现 |
| mruby-string-ext-latin9 | ISO-8859-15 转 UTF-8 |
| mruby-string-is-utf8 | UTF-8 有效性检查 |
| mruby-string-xor | 字符串 XOR |
| mruby-stringio | StringIO 类 |
| mruby-strptime | strptime 时间解析 |
| mruby-sysconf | sysconf 类 |
| mruby-sysrandom | 安全随机数生成 |
| mruby-tempfile | Tempfile 类 |
| mruby-thread | 线程库 |
| mruby-time-httpdate | HTTP 日期格式化 |
| mruby-time-strftime | Time#strftime |
| mruby-timer-thread | 定时器线程 |
| mruby-tiny-io | 小型 IO 库 |
| mruby-tiny-opt-parser | 命令行选项解析 |
| mruby-tinymt | TinyMT 随机数 |
| mruby-tinyxml2 | tinyxml-2 绑定 |
| mruby-toml | TOML 解析 |
| mruby-uname | uname 类 |
| mruby-unbound | Unbound DNS 客户端 (libunbound) |
| mruby-unicode-display_width | Unicode 字符宽度 |
| mruby-uri-parser | URI 解析器 |
| mruby-uriparser | URI 解析器 |
| mruby-userdata | 共享 userdata 对象 |
| mruby-uv | libuv 接口 |
| mruby-weakref | WeakRef 实现 |
| mruby-yaml | YAML 解析和输出 |
| typedargs | 运算符类型化 CLI 语言 |

---

## 需交叉编译外部 C 库 (24 个)

> 技术上可用但需要额外交叉编译外部 C 库。

| 库名 | 依赖的 C 库 |
|------|------------|
| mruby-argon2 | phc-winner-argon2 |
| mruby-bcrypt | (自包含 blowfish) |
| mruby-c-ares | c-ares |
| mruby-cipher | OpenSSL |
| mruby-curl | libcurl |
| mruby-geoip | GeoIP |
| mruby-http2 | nghttp2 |
| mruby-httpsclient | OpenSSL/mbedTLS |
| mruby-iconv | libiconv |
| mruby-libdeflate | libdeflate |
| mruby-libhydrogen | libhydrogen |
| mruby-libqrng | libqrng |
| mruby-libsodium | libsodium |
| mruby-lz4 | lz4 |
| mruby-lzma | lzma/xz |
| mruby-maxminddb | libmaxminddb |
| mruby-miniz | miniz (自包含) |
| mruby-passwdqc | passwdqc |
| mruby-polarssl | PolarSSL/mbedTLS |
| mruby-sqlite | sqlite3 |
| mruby-sqlite3 | sqlite3 |
| mruby-tls | libtls (LibreSSL) |
| mruby-zlib | zlib |
| mruby-zmq | libzmq |
| mruby-zstd | zstd |

---

## 不适用 (131 个)

> 依赖桌面 GUI、硬件外设、Linux 特定 API、数据库客户端等，不适合 Android。

| 库名 | 原因 |
|------|------|
| mruby-allegro | Allegro 5 游戏引擎绑定 |
| mruby-arduino | Arduino 硬件绑定 |
| mruby-audite | MP3 播放器 (libmpg123) |
| mruby-augeas | Augeas 配置编辑绑定 |
| mruby-bin-barista | DAG 任务构建工具 |
| mruby-bin-scite-mruby | SciTE 文本编辑器 |
| mruby-bin-theorem | 测试框架 |
| mruby-blendish | OUI-Blendish UI 绑定 |
| mruby-cache | 进程间共享内存缓存 |
| mruby-cgroup | Linux cgroup 绑定 |
| mruby-channel | 命名 FIFO 队列 |
| mruby-chipmunk2d | Chipmunk2D 物理引擎 |
| mruby-clang-plugin | Clang 静态检查插件 |
| mruby-cmake-build | CMake 构建生成器 |
| mruby-cocoa | macOS Cocoa 绑定 |
| mruby-concurrently | 基于 Fiber 的并发框架 |
| mruby-consul | Consul API 客户端 |
| mruby-cross-compile-on-mac-osx | macOS 交叉编译工具 |
| mruby-curses | ncurses/pdcurses 绑定 |
| mruby-cyberarm_engine | Gosu 游戏框架 |
| mruby-datadog | Datadog API 客户端 |
| mruby-disque | Disque 客户端 |
| mruby-dll | Windows DLL 支持 |
| mruby-esp32-gpio | ESP32 GPIO |
| mruby-esp32-i2c | ESP32 I2C |
| mruby-esp32-system | ESP32 系统 |
| mruby-esp32-wifi | ESP32 WiFi |
| mruby-etcd | etcd API 封装 |
| mruby-fiberpool | Fiber 池 |
| mruby-fltk3 | FLTK3 GUI 绑定 |
| mruby-fluent-logger | Fluentd 结构化日志 |
| mruby-ftp | FTP 客户端 |
| mruby-girffi | GObject Introspection FFI |
| mruby-gles | OpenGL ES 2.0 绑定 |
| mruby-glfw3 | GLFW3 绑定 |
| mruby-glib | GLib 标准库组件 |
| mruby-glib2 | GLib 2.x 绑定 |
| mruby-gobject | GObject 绑定 |
| mruby-gobject-introspection | GObject Introspection |
| mruby-gosu | Gosu 游戏库 |
| mruby-growthforecast | GrowthForecast 客户端 |
| mruby-gsl | GNU Scientific Library |
| mruby-gtk2 | GTK2 绑定 |
| mruby-gtk3 | GTK3 绑定 |
| mruby-heeler | 多进程 Web 服务器 |
| mruby-hibari | (未知，标记为桌面) |
| mruby-hiredis | hiredis 绑定 |
| mruby-hogun | (未知，标记为桌面) |
| mruby-host-stats | 主机统计 |
| mruby-inotify | Linux inotify 绑定 |
| mruby-io-console | IO/Console |
| mruby-io-uring | Linux io_uring |
| mruby-ionice | Linux ionice |
| mruby-ipfilter | IP 过滤 (Linux 特定) |
| mruby-ipvs | Linux IP Virtual Server |
| mruby-javascriptcore | WebKitGTK JS 绑定 |
| mruby-jvm | JVM 调用工具 |
| mruby-k2hash | k2hash 绑定 |
| mruby-leapmotion | Leap Motion SDK |
| mruby-leveldb | LevelDB 绑定 |
| mruby-linux-namespace | Linux namespace |
| mruby-lmdb | LMDB 绑定 |
| mruby-localmemcache | 共享内存缓存 |
| mruby-lua | Lua 绑定 |
| mruby-m2x | AT&T M2X IoT |
| mruby-mecab | MeCab 分词 |
| mruby-memcached | Memcached 客户端 |
| mruby-merb | Merb 框架 |
| mruby-minigame | 迷你游戏框架 |
| mruby-mod-mruby-ext | Apache mod_mruby |
| mruby-mrbgem-template | Gem 模板 |
| mruby-mrmagick | ImageMagick (需桌面库) |
| mruby-msagent | 微软 Agent |
| mruby-msd | MSD (未知) |
| mruby-mysql | MySQL 客户端 |
| mruby-nanovg | NanoVG 绑定 |
| mruby-netlink | Linux netlink |
| mruby-network-analyzer | 网络分析器 |
| mruby-ngx-mruby-ext | Nginx mruby 扩展 |
| mruby-odbc | ODBC 客户端 |
| mruby-oui | OUI 工具包 |
| mruby-pong | Pong 游戏 |
| mruby-postgresql | PostgreSQL 客户端 |
| mruby-process | 进程管理 |
| mruby-process2 | 进程管理 v2 |
| mruby-qml-parse | QML 解析 |
| mruby-qml-spawn | QML 生成 |
| mruby-raspberry | Raspberry Pi |
| mruby-rcon | RCON 客户端 |
| mruby-redis | Redis 客户端 |
| mruby-redis-ae | Redis (ae) |
| mruby-redis-cluster | Redis Cluster |
| mruby-renice | renice |
| mruby-research | 研究工具 |
| mruby-resource | 资源限制 |
| mruby-seccomp | seccomp |
| mruby-sftp | SFTP 客户端 |
| mruby-sftp-glob | SFTP glob |
| mruby-shelf | Rack-like web 框架 |
| mruby-shelf-deflater | Shelf 压缩中间件 |
| mruby-sidekiq-client | Sidekiq 客户端 |
| mruby-sinatic | Sinatra-like 框架 |
| mruby-spdy | SPDY 协议 |
| mruby-specinfra | 服务器测试工具 |
| mruby-ssh | SSH 客户端 |
| mruby-syslog | 系统日志 |
| mruby-tbot | Telegram Bot |
| mruby-termbox2 | Termbox2 终端 UI |
| mruby-terminal-table | 终端表格 |
| mruby-tls | (需 LibreSSL) |
| mruby-tty-screen | TTY 屏幕 |
| mruby-uchardet | 字符编码检测 |
| mruby-unbound | (需 libunbound) |
| mruby-uv | (需 libuv) |
| mruby-v8 | V8 JS 引擎 |
| mruby-vedis | Vedis 绑定 |
| mruby-virtualing | 虚拟化 |
| mruby-webkit-1 | WebKit 绑定 |
| mruby-webkit-3 | WebKit 绑定 |
| mruby-win32ole | Windows OLE |
| mruby-winapp | Windows 应用 |
| mruby-wiringpi | Raspberry Pi GPIO |
| mruby-wslay | WebSocket (需 C 库) |
| mruby-yeah | Yeah 游戏框架 |
| mruby-zabbix | Zabbix 客户端 |
| mruby-zest | Zest 框架 |
