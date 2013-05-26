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

(defn love [myid targetid loveword]
  (mc/insert "loves" { :myid myid :targetid targetid :loveword loveword})
  (let [ret (mc/find-maps "loves" { :myid targetid :targetid myid })]
    (if (mc/empty? ret)
      (format "请耐心等待，你喜欢的人 %s 或许也正暗恋着你" targetid)
      (format "你喜欢的人也喜欢你哦，ta对你说：%s" (:loveword (first ret))))))

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
    (let [[_ target word] (re-find content)
          ans (love from target word)]
      (format TEXT-TMPL from to (utils/get-time) "text" ans))))

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