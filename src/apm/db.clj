(ns apm.db
    (:use korma.db korma.core)
    (:require [lamina.core :as lam]))

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

(defn- insert-reference!
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
            (fields :id )
            (where { :fullname fullname }))]
        (when-not (empty? ret) ((first ret) :id))))

(defn- _create-reference-tree!
    "Creates a reference tree to the given reference name in the database and returns
    the highest ref-id in the tree

    If the name has a trailing slash it is assumed to be a directory. The reference isn't
    created if it already exists"
    [fullname]
    (when-not (= fullname "")
        (let [[path _] (path-split fullname)
              parent-id (_create-reference-tree! path)]
            (or (get-reference-id fullname)           ;First try to get the ref-id if it's already there
                (insert-reference! fullname parent-id) ;If it's not there, try and put it (insert-reference returns id)
                (get-reference-id fullname)))))       ;If something beat us to it (unlikely), get again

(defn get-reference-children
    "Given a ref-id or a fullname returns a vector of all the children of that reference,
    if it has any. Although a fullname can be given, a ref-id is preferred as the query
    for it is more efficient.

    Each reference in the vector takes the form of a map with :id, :fullname, and :isDir
    keys set"
    [fn-id] ;fullname-or-id
    (-> (select* reference)
        (fields :id :fullname :isDir )
        (#(if-not (string? fn-id)
            (where %1 {:parent_id fn-id})
            (where %1 {:parent_id (subselect reference
                                      (fields :id)
                                      (limit 1)
                                      (where {:fullname fn-id}))} )))
        (exec)))

(defn- _put-raw-value!
    "Given value data inserts into the databse and returns the val-id

    This will create the whole reference tree as well, if needed"
    [fullname raw-value]
    (let [ref-id (_create-reference-tree! fullname)
          ret
        (insert value
            (values { :ref_id ref-id
                      :value raw-value }))]
        (ret :GENERATED_KEY)))

(defn- _put-delta-of-value!
    "Given value name and a change for it, gets last value and adds the delta to it, and re-inserts
    a new value. Returns the new val-id"
    [fullname delta]
    (let [ref-id (_create-reference-tree! fullname)
          ret
        (transaction
            (let [select-ret (select value
                                (where { :ref_id ref-id })
                                (fields :value)
                                (order :id :DESC)
                                (limit 1))
                  last-val (if (empty? select-ret) 0 ((first select-ret) :value))]
            (insert value
                (values { :ref_id ref-id
                          :value (+ last-val delta) }))))]
    (ret :GENERATED_KEY)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Workers so mysql doesn't shit a brick
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def work-queue (lam/channel))

(defmacro undtest [ work-fn ]
    `(symbol (str "_" '~work-fn)))

(defmacro defworkfn [ work-fn ]
    `(defn ~work-fn [ & args# ]
        (let [prm# (promise)]
            (lam/enqueue work-queue [ (symbol (str "_" '~work-fn)) args# prm#])
            @prm#)))

(defworkfn create-reference-tree!)
(defworkfn put-raw-value!)
(defworkfn put-delta-of-value!)

(defn do-work []
    (let [[fun args prm] @(lam/read-channel work-queue)]
        fun ;No idea why this needs to be here....
        (deliver prm (apply (resolve fun) args))))

;One worker for now
(future (doall (repeatedly do-work)))
