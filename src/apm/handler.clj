(ns apm.handler
  (:use ring.adapter.jetty
        ring.middleware.reload)
  (:require [clojure.string :as s]))

(defn str-keyword? [string]
  (re-matches #"^:.*" string))

(def not-str-keyword? (complement str-keyword?))

(defn str-keyword->keyword [string]
  (keyword (s/replace string #"^:" "")))

(defn parse-uri 
  "Function that takes in the uri parts and will split them up, and convert them to keywords"
  [uri-parts]
  (let [reference (map keyword (take-while not-str-keyword? uri-parts))
        ;; Get the operations and turn the first element into a keyword
        operations (->
                      (drop-while not-str-keyword? uri-parts)
                      (vec)
                      (update-in [0] str-keyword->keyword))]
    [reference operations]))

(defn handler [request]
  (let [uri-parts (rest (s/split (:uri request) #"/"))
        request-method (:request-method request)]
    {:status 200
        :headers {"Content-Type" "text/html"}
        :body (str 
                "The raw URI is: " uri-parts 
                "<br/> "
                "The Request method was: " request-method 
                "<br/> "
                "The parsed uri is: " (parse-uri uri-parts))}))

(def app 
  (wrap-reload #'handler))

(comment 
  (def server (run-jetty #'app {:port 3000}))
  server
)


