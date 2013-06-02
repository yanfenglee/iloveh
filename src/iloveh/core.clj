(ns iloveh.core
  (:gen-class)
  (:require [iloveh.utils :as utils])
  (:require [monger.core :as mg])
  (:require [monger.result :as mr])
  (:require [monger.collection :as mc])
  (:use [compojure.route :only [files not-found] :as route]
     [compojure.handler :only [site]]
     [compojure.core :only [defroutes GET POST DELETE ANY context]]
     org.httpkit.server
     iloveh.msgtmpl))

;;;微信token
(def TOKEN "lyfpcy")

;;;返回信息
(def HELP "输入: @微信号 xxx，其中xxx为你对ta的表白，输入\"c\"查询是否有人喜欢你，输入\"h\"请求帮助")
(def REGISTER-HELP "请先注册信息, 输入你的微信号与电子邮件地址,即可完成注册，注意用空格隔开。\n例如: xiaoming123 xm1990@gmail.com\n")
(def DEFAULT-RETURN "请耐心等待，如果ta也喜欢你，我们会邮件通知你们俩的")
(def WELCOME-MSG (str "谢谢关注我们的帐号，" REGISTER-HELP HELP))

;;;微信url授权验证
(defn auth [req]
  (let [echostr (-> req :params :echostr)
        signature (-> req :params :signature)
        timestamp (-> req :params :timestamp)
        nonce (-> req :params :nonce)
        compstr (apply str (sort [TOKEN timestamp nonce]))
        hashv (utils/sha1 compstr)]
    (println signature timestamp nonce compstr hashv)
    (if (= signature hashv)
      echostr
      (println "auth failed!"))))

;;;格式化a对b说的话
(defn format-sweetwords [words]
  (apply str (map #(format "%s 喜欢 %s, %s 对 %s 说: %s \n" (:a %) (:b %) (:a %) (:b %) (:sweetwords %)) words)))

;;;发送邮件通知双方互相喜欢
(defn send-match-mail [a b asaid bsaid]
  (println "like each other " a b asaid bsaid)
  (str "恭喜你们俩，你们互相喜欢\n" (format-sweetwords asaid) "\n" (format-sweetwords bsaid)))

;;;注册用户
(defn register [openid name email]
  (if (mr/ok? (mc/insert "users" {:openid openid :name name :email email}))
    (str "注册成功! " HELP)
    "数据库错误，注册失败"))

;;;判断a是否喜欢b
(defn like? [a b]
  (let [ret (mc/find-maps "messages" {:a a :b b})]
    (if (empty? ret)
      [false ""]
      [true ret])))

;;;查看是否相互喜欢
(defn like-each-other? [a b]
  (let [[alikeb asaid] (like? a b)
        [blikea bsaid] (like? b a)]
    (if (and alikeb blikea)
      [true asaid bsaid]
      [false "" ""])))

(defn get-register-info [openid]
  (mc/find-one-as-map "users" {:openid openid}))

(defn registered? [openid]
  (not= (get-register-info openid) nil))

;;;你喜欢的人和喜欢你的人是否匹配
(defn match-message [be-liked like]
  (println be-liked like)
  (if (empty? be-liked)
    "请耐心等待"
    (if (empty? like)
      "有人喜欢你哦，快@你喜欢的人，看看你们是否相互喜欢"
      (let [matches (for [x be-liked,y like :when (and (= (:a x) (:b y)) (= (:a y) (:b x)))] [x y])]
        (if (empty? matches)
          "发现有喜欢你的人，但ta并非你喜欢的，想想会是谁呢？"
          (let [asaid (reduce #(conj %1 %2) #{} (map #(first %) matches))
                bsaid (reduce #(conj %1 %2) #{} (map #(second %) matches))]
            (str (format-sweetwords asaid) "\n----------\n" (format-sweetwords bsaid))))))))

(defn check-liked [openid]
  (let [info (get-register-info openid)]
    (if (nil? info)
      REGISTER-HELP
		  (let [be-liked (mc/find-maps "messages" {:b (:name info)})
          like (mc/find-maps "messages" {:a (:name info)})]
		    (match-message be-liked like)))))

;;;处理用户的表白信息
(defn speak-to-ta [your-openid ta sweetwords]
  (let [ret (get-register-info your-openid)]
    (if (nil? ret)
      REGISTER-HELP
      (let [you (:name ret)]
        (if (mr/ok? (mc/insert "messages" {:a you :b ta :sweetwords sweetwords}))
          (let [[match tasaid yousaid] (like-each-other? ta you)]
            (if match
              (send-match-mail ta you tasaid yousaid)
              DEFAULT-RETURN))
          "数据库插入错误")))))

(defn parse-message [msg]
  (re-find #"\s*@(\w{6,})\s*(.*)" msg))

(defn parse-register [msg]
  (re-find #"\s*(\w{6,})\s*((\w+\.)*\w+@(\w+\.)+[A-Za-z]+)" msg))

(defn reply-text [from to content]
  (println "reply msg" from to content)
  (format TEXT-TMPL from to (utils/get-time) "text" content))

(defn reply [poststr]
  (println poststr)
  (let [xs (utils/xml-parse-str poststr)
        to (utils/xml-find :ToUserName xs)
        from (utils/xml-find :FromUserName xs)
        msgtype (utils/xml-find :MsgType xs)
        resp #(reply-text from to %)]
    (println "===================================")
    (println to from msgtype)
    (println "-----------------------------------")
    (case msgtype
      "text" (let [content (utils/xml-find :Content xs)]
               (case content
                 ("h" "H") (resp (if (registered? from) HELP REGISTER-HELP))
						     ("c" "C") (resp (check-liked from))           
							   (if (registered? from)
                   (let [ret (parse-message content)]
                     (if (nil? ret)
							         (resp HELP)
							         (let [[_ ta sweetwords] ret]
							           (resp (speak-to-ta from ta sweetwords)))))
                   (let [ret (parse-register content)]
                     (if (nil? ret)
                       (resp REGISTER-HELP)
                       (let [[_ name email] ret]
                         (resp (register from name email))))))))
      "event" (let [ev (utils/xml-find :Event xs)]
                (case ev
                  "subscribe" (resp WELCOME-MSG)
                  "unsubscribe" (resp "unsubscribed!")
                  "CLICK" (resp "clicked")))
      (resp "目前不支持此消息类型"))))


(defroutes all-routes
  (GET "/auth" [] auth)
  (POST "/auth" {body :body} (reply (slurp body)))
  (route/not-found "<p>Page not found.</p>"))

(defn init-db []
  (mg/connect-via-uri! "mongodb://127.0.0.1/weixin"))

(defn -main [& args]
  (init-db)
  (run-server (site #'all-routes) {:port 80})
  (println "run server..."))