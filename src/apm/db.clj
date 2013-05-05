(ns apm.db
    (:use korma.db korma.core)
    (:require [lamina.core :as lam]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Workers so mysql doesn't shit a brick
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def work-queue (lam/channel))

(defmacro defworker [ fn-name doc args & body ]
    (let [und-fn-name (symbol (str "_" fn-name))]
        `(do
            (defn ~und-fn-name ~args ~@body)
            (defn ~fn-name ~doc ~args
                (let [prm# (promise)]
                    (lam/enqueue work-queue [ ~und-fn-name ~args prm#])
                    @prm#)))))

(defn do-work [somework]
    (let [[fun args prm] somework
           answer (apply fun args)]
        (deliver prm answer)))

(lam/receive-all work-queue do-work)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Actual db stuff
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare reference value)

(defn init-db
    "Pass in map with :host, :port, :db, :user, and :password"
    [config]
    (defdb apmdb (mysql config))

    (defentity reference
        (database apmdb)
        (table :ref)
        (has-many value)
        (has-one reference {:fk :parent_id}))

    (defentity value
        (database apmdb)
        (has-one reference)))

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

(defworker create-reference-tree!
    "Creates a reference tree to the given reference name in the database and returns
    the highest ref-id in the tree

    If the name has a trailing slash it is assumed to be a directory. The reference isn't
    created if it already exists"
    [fullname]
    (when-not (= fullname "")
        (let [[path _] (path-split fullname)]

            ;First try to get the ref-id if it's already there
            (or (get-reference-id fullname)

            ;If it's not there, try and put it
                (insert-reference! fullname (_create-reference-tree! path))

            ;If something beat us to it (unlikely), get again
                (get-reference-id fullname)))))

(defn- where-id
    "Helps select against either a ref-id or ref-name"
    [sel-ref id-sel fn-id]
    (if-not (string? fn-id)
            (where sel-ref {id-sel fn-id})
            (where sel-ref {id-sel (subselect reference
                                   (fields :id)
                                   (limit 1)
                                   (where {:fullname fn-id}))} )))

(defn get-dir-children
    "Given a ref-id or a fullname returns a vector of all the children of that dir,
    if it has any. Although a fullname can be given, a ref-id is preferred as the query
    for it is more efficient.

    Each reference in the vector takes the form of a map with :id, :fullname, and :isDir
    keys set"
    [fn-id] ;fullname-or-id
    (-> (select* reference)
        (fields :id :fullname :isDir )
        (where-id :parent_id fn-id)
        (exec)))

(defn get-all-reference-values
    [fn-id]
    "Given ref-id or a fullname returns a vector of all the associated values.  Although a
    fullname can be given, a ref-id is preferred as the query for it is more efficent.  The
    values are returned ordered by ascending time.

    Each item in the returned vector is a map with :id, :value, and :ts"
    [fn-id]
    (-> (select* value)
        (fields :id :value :ts)
        (where-id :ref_id fn-id)
        (order :id :asc)
        (exec)))

(defn get-reference-values-by-seq
    "Given ref-id or a fullname returns a vector of the most recent lim values, offset off
    from the front of the list. Although a fullname can be given, a ref-id is preferred as
    the query for it is more efficent. The values are returned ordered by ascending time.

    Each item in the returned vector is a map with :id, :value, and :ts"
    [fn-id lim off]
    (-> (select* value)
        (fields :id :value :ts)
        (where-id :ref_id fn-id)
        (order :id :desc)
        (limit lim)
        (offset off)
        (exec)
        (reverse)))

(defn get-reference-values-by-date
    "Given ref-id or a fullname returns a vector of the most recent values starting at time
    start and ending at end (inclusive). Although a fullname can be given, a ref-id is
    preferred as the query for it is more efficient. The values are returned ordered by
    ascending time. start and end both should be Java.util.Dates (using date-clj makes this
    super simple).

    Each item in the returned vector is a map with :id, :value, and :ts"
    [fn-id start end]
    (-> (select* value)
        (fields :id :value :ts)
        (where-id :ref_id fn-id)
        (where (and (>= :ts start)
                    (<= :ts end)))
        (order :id :asc)
        (exec)))

(defworker put-raw-value!
    "Given value data inserts into the databse and returns the val-id

    This will create the whole reference tree as well, if needed"
    [fullname raw-value]
    (let [ref-id (_create-reference-tree! fullname)
          ret
        (insert value
            (values { :ref_id ref-id
                      :value raw-value }))]
        (ret :GENERATED_KEY)))

(defworker put-delta-of-value!
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

