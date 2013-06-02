(ns iloveh.utils
  (:import java.security.MessageDigest
           java.util.Date)
  (:require [clojure.xml :as xml])
  (:require [clojure.zip :as zip]))

(defn xml-parse-str [s]
  (xml/parse (java.io.ByteArrayInputStream. (.getBytes s))))

(defn zip-str [s]
  (zip/xml-zip (xml-parse-str s)))

(defn xml-find [key xmls]
  (first (for [x (xml-seq xmls)
               :when (= key (:tag x))]
           (first (:content x)))))


(defn get-hash [type data]
  (.digest (java.security.MessageDigest/getInstance type) (.getBytes data) ))
 
(defn sha1-hash [data]
  (get-hash "sha1" data))
 
(defn get-hash-str [data-bytes]
  (apply str 
	(map 
		#(.substring 
			(Integer/toString 
		(+ (bit-and % 0xff) 0x100) 16) 1) 
		data-bytes)
	))

(defn sha1 [strdata]
  (get-hash-str (sha1-hash strdata)))

(defn get-time []
  (.getTime (new java.util.Date))) 



