(defproject meve "0.1.0-SNAPSHOT"
  :description "metosin event sourcing"
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :profiles {:dev {:dependencies [[midje "1.5.1"]]
                   :plugins [[lein-midje "2.0.1"]]}}
  :main meve.server
  :repl-options {:init-ns meve.server}
  :min-lein-version "2.0.0")
