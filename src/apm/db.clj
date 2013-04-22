(ns apm.db
    (:use korma.db korma.core))

(defdb apmdb (mysql { :db       "apm"
                      :user     "apm"
                      :password "need more pylons" }))

(declare reference value)

(defentity reference
    (database apmdb)
    (table :ref)
    (has-many value)
    (has-one reference {:fk :parent_id}))

(defentity value
    (database apmdb)
    (has-one reference))

(defn- path-split [path]
    (rest (re-find #"(.*?)([^\/]*\/?)$" path)))

(defn- insert-reference
    "Inserts and returns the new ref-id, or returns nil if it already existed"
    [fullname parent-id]
    (let [is-dir (if (= \/ (last fullname)) 1 0)
          ret (exec-raw apmdb ["INSERT IGNORE INTO `ref` ( `fullname`, `parent_id`, `isDir` ) VALUES ( ?, ?, ? )",
                              [fullname parent-id is-dir]] :keys)]
        (when (map? ret) (ret :GENERATED_KEYS))))

(defn get-reference-id
    "Returns the ref-id associated with the given name, or nil if none is found"
    [fullname]
    (let [ret
        (select reference
            (fields [ :id ])
            (where { :fullname fullname }))]
        (when-not (empty? ret) ((first ret) :id))))

(defn create-reference-tree
    "Creates a reference tree to the given reference name in the database and returns
    the highest ref-id in the tree

    If the name has a trailing slash it is assumed to be a directory. The reference isn't
    created if it already exists"
    [fullname]
    (when-not (= fullname "")
        (let [[path _] (path-split fullname)
              parent-id (create-reference-tree path)]
            (or (get-reference-id fullname)           ;First try to get the ref-id if it's already there
                (insert-reference fullname parent-id) ;If it's not there, try and put it (insert-reference returns id)
                (get-reference-id fullname)))))       ;If something beat us to it (unlikely), get again


(defn put-raw-value
    "Given value data inserts into the databse and returns the val-id

    This will create the whole reference tree as well, if needed"
    [fullname raw-value]
    (let [ref-id (create-reference-tree fullname)
          ret
        (insert value
            (values { :ref_id ref-id
                      :value raw-value }))]
        (ret :GENERATED_KEY)))
