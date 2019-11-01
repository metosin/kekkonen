(defproject sample "0.1.0-SNAPSHOT"
  :description "Kekkonen with Component sample"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [http-kit "2.1.19"]
                 [com.stuartsierra/component "0.3.1"]
                 [reloaded.repl "0.2.2"]
                 [metosin/palikka "0.5.1"]
                 [metosin/kekkonen "0.5.0"]]
  :profiles {:uberjar {:aot [sample.main]
                       :main sample.main
                       :uberjar-name "sample.jar"}})
