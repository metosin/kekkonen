(defproject metosin/kekkonen-core "0.5.2-SNAPSHOT"
  :description "A lightweight, remote api library for Clojure."
  :url "https://github.com/metosin/kekkonen"
  :license {:name "Eclipse Public License" :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-parent "0.3.4"]]
  :parent-project {:path "../../project.clj"
                   :inherit [:deploy-repositories :managed-dependencies]}
  :dependencies [[prismatic/plumbing]
                 [prismatic/schema]
                 [frankiesardo/linked]

                 ;; http-stuff, separate module?
                 [metosin/ring-swagger]
                 [metosin/ring-swagger-ui]
                 [metosin/ring-http-response]
                 [metosin/muuntaja]
                 [ring/ring-defaults]

                 ;; client stuff, separate module?
                 [clj-http]])
