(defproject metosin/kekkonen "0.1.0-SNAPSHOT"
  :description "Remote APIs for Clojure(Script)"
  :url "https://github.com/metosin/kekkonen"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :dependencies [[prismatic/plumbing "0.5.1"]
                 [prismatic/schema "1.0.2"]

                 ;; http-stuff, separate module?
                 [metosin/ring-swagger "0.22.0-SNAPSHOT"]
                 [metosin/ring-swagger-ui "2.1.3"]
                 [metosin/ring-middleware-format "0.6.0"]
                 [metosin/ring-http-response "0.6.5"]

                 ;; client stuff, separate module?
                 [clj-http "2.0.0"]]
  :profiles {:dev {:plugins [[lein-midje "3.2"]]
                   :source-paths ["dev-src" "src"]
                   :dependencies [[org.clojure/clojure "1.7.0"]
                                  [criterium "0.4.3"]
                                  [http-kit "2.1.19"]
                                  [midje "1.7.0"]]}
             :perf {:jvm-opts ^:replace []}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}}
  :aliases {"all" ["with-profile" "dev:dev,1.6"]
            "perf" ["with-profile" "default,dev,perf"]
            "test-ancient" ["midje"]})
