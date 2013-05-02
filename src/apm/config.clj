(ns apm.config
    (:require [clojure.tools.reader.edn :as edn]))

(def example-config-str "
; This is an example configuration for edn. It contains all the default values for the various
; options, change them as needed.
{

    ;Information apm needs to talk to the mysql server it will be restoring/retrieving info from
    :mysql {
             ;Hostname mysql lives on. Could also be ip-address (also as string)
             :host \"localhost\"
             
             ;Port mysql lives on
             :port 3306

             ;Database name that the apm schema has been set up on. See the schema/schema.sql file
             ;in the repo to get the schema import file
             :db   \"apm\"

             ;User that has SELECT and INSERT permissions on the database specified above
             :user \"FILL ME IN\"

             ;Password for the user specified above
             :password \"FILL ME IN\"
           }
    
    ;Options for the HTTP REST API (OMG CAPS)
    :rest {
            :enabled true
            :port    9001
          }

    ;Options for udp socket
    :udp {
           :enabled true
           :port    9002
         }

}
")

(def config-atom (atom {}))

(defn get
    "Given key you want from the config, returns that key. Key can be multiple layers deep, for
    instance: (get :mysql :user)"
    [& keys]
    (get-in @config-atom keys))

(defn put-default-config 
    "Given a filename puts the default config there (complete with comments)"
    [filename]
    (spit filename example-config-str))

(defn load-config
    "Given a filename, loads it in as configuration"
    [filename]
    (->> filename
         (slurp)
         (edn/read-string)
         (reset! config-atom)))
