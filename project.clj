(defproject apm "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [ring/ring-core "1.1.8"]
                 [ring/ring-jetty-adapter "1.1.6"]
                 [ring/ring-devel "1.1.8"]]
  :plugins [[lein-ring "0.8.2"]]
  :ring {:handler apm.handler/app}
  :profiles
  {:dev {:dependencies [[ring-mock "0.1.3"]]}})
