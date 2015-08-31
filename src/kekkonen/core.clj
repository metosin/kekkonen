(ns kekkonen.core
  (:require [schema.core :as s]
            [plumbing.core :as p]
            [clojure.string :as str]
            [clojure.walk :as w]
            [plumbing.fnk.pfnk :as pfnk])
  (:import [clojure.lang Var Keyword IPersistentMap]))

;;
;; Definitions
;;

(s/defschema Function
  (s/=> {s/Keyword s/Any} s/Any))

(s/defn ^:private user-meta [v :- (s/either Var Function)]
  (-> v meta (dissoc :schema :handler :ns :name :file :column :line :doc :description :plumbing.fnk.impl/positional-info)))

(s/defschema Handler
  "Action handler metadata"
  {:fn Function
   :name s/Keyword
   :type s/Keyword
   :module s/Keyword
   :user {s/Keyword s/Any}
   :description (s/maybe s/Str)
   :input s/Any
   :output s/Any
   (s/optional-key :source-map) {:line s/Int
                                 :column s/Int
                                 :file s/Str
                                 :ns s/Symbol
                                 :name s/Symbol}
   s/Keyword s/Any})

(s/defn handler [meta :- {s/Keyword s/Any} f :- Function]
  (vary-meta f merge {:handler true} meta))

(defn handler? [x]
  (and (map? x) (:fn x) (:type x)))

(s/defschema Modules
  "Modules form a tree."
  {s/Keyword s/Any #_(s/either [Handler] (s/recursive #'Modules))})

(s/defschema Kekkonen
  {:modules Modules
   :inject {s/Keyword s/Any}
   s/Keyword s/Any})

;;
;; Collecting
;;

(defprotocol HandlerCollector
  (-collect [this type-resolver]))

(s/defn collect [collector type-resolver]
  (-collect collector type-resolver))

(defrecord CollectVar [v]
  HandlerCollector
  (-collect [_ type-resolver]
    (println "collecting a var " v)
    (let [{:keys [line column file ns name doc schema] :as meta} (meta v)]
      (if-let [handler (if schema
                         {:fn @v
                          :name (keyword name)
                          :user (user-meta v)
                          :description doc
                          :input (pfnk/input-schema @v)
                          :output (pfnk/output-schema @v)
                          :source-map {:line line
                                       :column column
                                       :file file
                                       :ns (ns-name ns)
                                       :name name}})]
        (type-resolver handler meta)))))

(defn collect-var [v]
  (->CollectVar v))

(defrecord CollectFn [f]
  HandlerCollector
  (-collect [_ type-resolver]
    (if-let [{:keys [name description schema] :as meta} (meta f)]
      (if-let [handler (if (and name schema)
                         {:fn f
                          :name (keyword name)
                          :user (user-meta f)
                          :description description
                          :input (pfnk/input-schema f)
                          :output (pfnk/output-schema f)})]
        (type-resolver handler meta)))))

(defn collect-fn
  [f] (->CollectFn f))

(defrecord CollectNs [ns]
  HandlerCollector
  (-collect [_ type-resolver]
    (println "collecting a ns " ns)
    (require ns)
    (p/for-map [handler (some->> ns
                                 ns-publics
                                 (map (comp collect-var val))
                                 (keep #(-collect % type-resolver)))]
      (:name handler) handler)))

(defn collect-ns
  [ns] (->CollectNs ns))

(extend-type IPersistentMap
  HandlerCollector
  (collect [this type-resolver]
    (p/for-map [[k v] this]
      k (-collect v type-resolver))))

;;
;; Registry
;;

(defn default-type-resolver [handler meta]
  (if (some-> meta :handler true?)
    (assoc handler :type :handler)))

(p/defnk create :- Kekkonen
  "Creates a Kekkonen."
  [modules :- Modules
   {type-resolver :- s/Any default-type-resolver}
   {inject :- {s/Keyword s/Any} {}}]
  (let [->handler (fn [h k m]
                    (let [module (->> k (conj m) (map name) (str/join "/") keyword)]
                      (assoc (type-resolver h) :module module)))
        traverse (fn f [x m]
                   (p/for-map [[k v] x]
                     k (if (handler? v)
                         {(:name v) (->handler v k m)}
                         (f v (conj m k)))))]
    {:inject inject
     :modules (traverse (collect modules type-resolver) [])}))

(s/defn ^:private action-kws [path :- s/Keyword]
  (-> path str (subs 1) (str/split #"/") (->> (mapv keyword))))

(s/defn some-handler :- (s/maybe Handler)
  "Returns a handler or nil"
  [kekkonen, action :- s/Keyword]
  (get-in (:modules kekkonen) (action-kws action)))

(s/defn all-handlers :- [Handler]
  "Returns all handlers."
  [kekkonen]
  (let [handlers (atom [])]
    (w/prewalk
      (fn [x]
        (if (handler? x)
          (do (swap! handlers conj x) nil)
          x))
      (:modules kekkonen))
    @handlers))

(s/defn invoke
  "Invokes a action handler with the given context."
  ([kekkonen action]
    (invoke kekkonen action {}))
  ([kekkonen action request]
    (let [handler (some-handler kekkonen action)
          ; context-level overrides of inject!
          context (merge (:inject kekkonen) request)]
      (if-not handler
        (throw (ex-info (str "invalid action " action) {}))
        ((:fn handler) context)))))

(p/defnk ^:handler tst [])

(-> 'kekkonen.core
    collect-ns
    (collect default-type-resolver))

