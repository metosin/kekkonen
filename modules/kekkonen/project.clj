(defproject metosin/kekkonen "0.5.0-SNAPSHOT"
  :description "A lightweight, remote api library for Clojure."
  :url "https://github.com/metosin/kekkonen"
  :license {:name "Eclipse Public License" :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-parent "0.3.4"]]
  :parent-project {:path "../../project.clj"
                   :inherit [:deploy-repositories :managed-dependencies]}
  :dependencies [[metosin/kekkonen-core]])
