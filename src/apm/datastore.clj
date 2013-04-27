;; Abstract away the datastore bits
(ns apm.datastore)


;; For now lets just use a simple atom
(def data (atom {}))

