(ns apm.dispatch
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

(def dispatch-map {
    ;TODO the get methods need stuff in apm.db to be implemented
    ":get_:nomod"   debug-dispatch
    ":get_:by-date" debug-dispatch
    ":get_:by-seq"  debug-dispatch

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
