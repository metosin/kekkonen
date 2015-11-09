(defproject sample "0.1.0-SNAPSHOT"
  :description "Kekkonen with Component sample"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [http-kit "2.1.19"]
                 [com.stuartsierra/component "0.3.0"]
                 [reloaded.repl "0.2.1"]
                 [metosin/palikka "0.3.0"]
                 [metosin/kekkonen "0.1.0-SNAPSHOT"]]
  :profiles {:uberjar {:aot [sample.main]
                       :main sample.main
                       :uberjar-name "sample.jar"}})
