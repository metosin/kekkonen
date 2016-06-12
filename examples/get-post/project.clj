(defproject get-post "0.1.0-SNAPSHOT"
  :description "Extended examples"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [http-kit "2.1.19"]
                 [clj-http "2.2.0"]
                 [metosin/kekkonen "0.3.0-SNAPSHOT"]]
  :main get-post.core
  :profiles {:dev {:dependencies [[midje "1.6.3"]
                                  [ring-mock "0.1.5"]]}})
