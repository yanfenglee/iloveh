(ns iloveh.core
  (:gen-class)
  (:require [iloveh.utils :as utils])
  (:require [monger.core :as mg])
  (:require [monger.collection :as mc])
  (:use [compojure.route :only [files not-found] :as route]
     [compojure.handler :only [site]]
     [compojure.core :only [defroutes GET POST DELETE ANY context]]
     org.httpkit.server
     iloveh.msgtmpl))

(def TOKEN "lyfpcy")

(defn infos [req]
  (mc/find-maps "loves"))

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



(defn love [fromid a b loveword]
  (println fromid a b loveword)
  (let [ret (mc/find-maps "loves" {:fromid fromid})]
    (if (or (empty? ret) (= (:a ret) a))
		  (do (mc/insert "loves" {:fromid fromid :a a :b b :loveword loveword})
      (let [ret2 (mc/find-maps "loves" {:a b :b a})]
		    (if (empty? ret2)
		      (format "请耐心等待，你喜欢的人 %s 或许也正暗恋着你" b)
		      (format "你喜欢的人也喜欢你哦，ta对你说：%s" (:loveword (first ret2))))))
    (format "你是 %s 吧？ 不能用别的微信号哦:)" (:a (first ret))))))

(defn checklove [fromid]
  (let [ret (mc/find-maps "loves" {:fromid fromid})]
    (if (empty? ret)
      (format "你还没有告诉我你喜欢谁哦：），请先告诉我你喜欢谁，才能查询是否有人也喜欢你哦^_^")
      (let [a (:a ret)
            ret2 (mc/find-maps "loves" {:b a})]
        (if (empty? ret2)
          (format "请耐心等待，你喜欢的人或许还没不认识我哦，你可以跟ta介绍下我呀^_^")
          (format "恭喜你，%s 喜欢你哦，快和ta联系吧:)" (:a (first ret2))))))))


(defn parsecontent [content]
  (re-find #"^@(\w{6,})\s*喜欢\s*@(\w{6,})\s*(.*)" content))

(defn reply-text [from to content]
  (format TEXT-TMPL from to (utils/get-time) "text" content))

(defn reply [poststr]
  (println poststr)
  (let [xs (utils/xml-parse-str poststr)
        to (utils/xml-find :ToUserName xs)
        from (utils/xml-find :FromUserName xs)
        msgtype (utils/xml-find :MsgType xs)
        content (utils/xml-find :Content xs)]
    (println "===================================")
    (println to from msgtype content)
    (println "-----------------------------------")
    (if (or (= content 'c') (= content 'C'))
      (reply-text from to (checklove from))
	    (let [ret (parsecontent content)]
	      (if (nil? ret)
	        (reply-text from to "输入格式不对哦, 请输入 @A喜欢@B ")
	        (let [[_ a b loveword] ret]
	          (reply-text from to (love from a b loveword))))))))

(defroutes all-routes
  (GET "/auth" [] auth)
  (POST "/auth" {body :body} (reply (slurp body)))
  (GET "/love" [] love)
  (GET "/infos" [] infos)
  (route/not-found "<p>Page not found.</p>"))

(defn init-db []
  (mg/connect-via-uri! "mongodb://127.0.0.1/test2"))


(defn -main [& args]
  (init-db)
  (run-server (site #'all-routes) {:port 80})
  (println "run server..."))