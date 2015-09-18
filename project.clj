(defproject metosin/kekkonen "0.0.1-SNAPSHOT"
  :description "Kekkonen, a lightweight CQRS frontend for Clojure"
  :url "https://github.com/metosin/kekkonen"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :dependencies [[prismatic/plumbing "0.5.0"]
                 [prismatic/schema "0.4.4"]
                 [metosin/ring-swagger "0.21.0"]
                 [metosin/ring-swagger-ui "2.1.2"]
                 [metosin/ring-middleware-format "0.6.0"]
                 [metosin/ring-http-response "0.6.5"]

                 ;; client stuff, separate module?
                 [clj-http "2.0.0"]]
  :profiles {:dev {:plugins [[lein-midje "3.1.3"]]
                   :source-paths ["dev-src" "src"]
                   :dependencies [[org.clojure/clojure "1.7.0"]
                                  [http-kit "2.1.19"]
                                  [midje "1.7.0"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}}
  :aliases {"all" ["with-profile" "dev:dev,1.6"]
            "test-ancient" ["midje"]})
