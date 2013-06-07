(ns apm.dispatch
    (:use date-clj)
    (:require [apm.db :as db]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; MARCO:
;; The method you want is dispatch, it's the very last method
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- debug-dispatch  [ & args ] {:error false :result args})

(defn- do-incdec
    [ref-name mult potential-delta]
    (let [delta (if (empty? potential-delta) 1 (first potential-delta))]
        (db/put-delta-of-value! ref-name (* mult (Integer/valueOf delta)))))

(defn- date->ts [d]  (int (/ (.getTime d) 1000)))
(defn- ts->date [ts] (date (* (Integer/valueOf ts) 1000)))
(defn- format-get-results [res] (map #(assoc %1 :ts (date->ts (%1 :ts))) res))

(def dispatch-map {

    ":get_:nomod"   (fn [req-type ref-name req-mod]
                        (let [real-ref-name (if (= "/" ref-name) "/" (str ref-name "/"))]
                            { :error false
                              :results (db/get-dir-children real-ref-name) }))

    ":get_:all"     (fn [req-type ref-name req-mod]
                        { :error false
                          :results (format-get-results (db/get-all-reference-values ref-name)) })

    ":get_:by-date" (fn [req-type ref-name req-mod start-ts & ending-ts?]
                        (let [start (ts->date start-ts)
                              end (if (empty? ending-ts?) (date)
                                                          (ts->date (first ending-ts?)))]
                            { :error false
                              :results (format-get-results
                                        (db/get-reference-values-by-date ref-name start end)) }))

    ":get_:by-seq"  (fn [req-type ref-name req-mod lim-str & off-str?]
                        (let [lim (Integer/valueOf lim-str)
                              off (if (empty? off-str?) 0 (Integer/valueOf (first off-str?)))]
                            { :error false
                              :results (format-get-results
                                        (db/get-reference-values-by-seq ref-name lim off)) }))

    ":post_:abs"    (fn [req-type ref-name req-mod abs-val]
                        (db/put-raw-value! ref-name (Integer/valueOf abs-val))
                        {:error false})

    ":post_:inc"    (fn [req-type ref-name req-mod & delta-val]
                        (do-incdec ref-name 1 delta-val)
                        {:error false})

    ":post_:dec"    (fn [req-type ref-name req-mod & delta-val]
                        (do-incdec ref-name -1 delta-val)
                        {:error false})
})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Actual dispatch utilities
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- map-request
    "Given request information returns a map with the following fields
    (potentially) filled in:

    :type   - either :get or :post
    :refval - the name of the reference/value being modified/get'd
    :mod    - modifier on the request, like :inc or :abs
    :args   - list of any parts coming after the modifier"
    [req-type uri-parts]
    (reduce (fn [request part]
                (cond (= (first part) \:)
                          (assoc request :mod part)
                      (nil? (request :mod))
                          (assoc request :refval
                              (str (request :refval "") \/ part))
                      :else
                          (assoc request :args
                              (conj (request :args []) part))))
            {:type req-type} uri-parts))

(defn- req-name
    [req-map]
    (str (req-map :type) "_"
         (req-map :mod :nomod)))

(defn- no-matching-req [ & args ] {:error true  :error-str "No handler to match the query"})

(defn- actually-dispatch
    [req-map]
    (let [req-fn (dispatch-map (req-name req-map) no-matching-req)
          args-prefix [(req-map :type :get)
                       (req-map :refval "/")
                       (req-map :mod :nomod)]]
        (try
            (apply req-fn (concat args-prefix (req-map :args [])))
        (catch Exception e {:error true :exception true :error-str (.getMessage e)}))))
    

(defn dispatch
    "Takes in req-type, either :get or :post, as well as a vector
    of the uri-parts. On a successful/valid request returns:
    { :error  false
      :result <Results of the request as a (hopefully) json parseable data
               structure, or nil> }

    on a failed request:
    { :error true
      :error-str \"Some string describing the error\" }

    If the request fails as the result of an exception, the :exception
    key will be set to true as well"
    [req-type uri-parts]
    (-> (map-request req-type uri-parts)
        (actually-dispatch)))

(db/init-db
 {:db   "apm" :user "root"})

