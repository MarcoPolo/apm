(ns apm.db
    (:use korma.db korma.core))

(defdb apmdb (mysql { :db       "apm"
                      :user     "apm"
                      :password "need more pylons" }))

(declare reference value)

(defentity reference
    (database apmdb)
    (table :ref)
    (has-many value))

(defentity value
    (database apmdb)
    (has-one reference))

(defn create-reference
    "Given reference data inserts into the database and returns the ref-id"
    [ref-name dir]
    (let [ret
            (insert reference
                (values { :name ref-name
                    :dir  dir }))]
        (ret :GENERATED_KEY)))

(defn get-reference-id
    "Given reference data returns the id for that reference, or nil"
    [ref-name dir]
    (let [ret
        (select reference
            (where { :name ref-name
                     :dir  dir })
            (fields [:id]))]
        (if (empty? ret) nil
            ((first ret) :id))))

(defn- path-split [path]
    (rest (re-find #"(.+?)([^\/]*)$" path)))

(defn put-raw-value
    "Given value data inserts into the databse and returns the val-id

    Doesn't do any error checking whatsoever, and assumes the reference
    already exists. The schema's probably gonna change so I didnt' put much
    effort into this"
    [val-name raw-value]
    (let [[dir ref-name] (path-split val-name)
          ref-id (get-reference-id ref-name dir)
          ret
        (insert value
            (values { :ref_id ref-id
                      :value raw-value }))]
        (ret :GENERATED_KEY)))
