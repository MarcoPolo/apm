(defproject apm "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.0"]
                 [org.clojure/tools.reader "0.7.4"]
                 [date-clj "1.0.1"]
                 [korma "0.3.0-RC5"]
                 [lamina "0.5.0-beta15"]
                 [mysql/mysql-connector-java "5.1.6"]
                 [ring/ring-core "1.1.8"]
                 [ring/ring-jetty-adapter "1.1.6"]
                 [ring/ring-devel "1.1.8"]]
  :plugins [[lein-ring "0.8.2"]]
  :ring {:handler apm.handler/app}
  :profiles
  {:dev {:dependencies [[ring-mock "0.1.3"]]}})
