(ns apm.handler
  (:use ring.adapter.jetty
        ring.middleware.reload)
  (:require [clojure.string :as s]))

(def data (atom {}))

(re-matches #":.*" ":asdf")


;(retrieve-data ["dsf"])

(defn retrieve-data [uri-parts]
  (get-in 
    @data
    (map keyword uri-parts) 
    []))

(defn str-keyword? [string]
  (nil? (re-matches #":.*" string)))

(.getTime (java.util.Date.) )
(.. java.util.Date. )


(defn work-with-data [uri-parts]
  (let [reference (map keyword (take-while str-keyword? uri-parts))
        operations (drop-while str-keyword? uri-parts)]))


(defn handler [request]
  (let [uri-parts (rest (s/split (:uri request) #"/"))
        request-method (:request-method request)]
    {:status 200
        :headers {"Content-Type" "text/html"}
        :body (str "Hello World" uri-parts request-method)}))

(def app 
  (wrap-reload #'handler))

(comment 
  (def server (run-jetty #'app {:port 3000}))
  server
)


