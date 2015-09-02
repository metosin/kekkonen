(ns kekkonen.core
  (:require [schema.core :as s]
            [plumbing.core :as p]
            [clojure.string :as str]
            [clojure.walk :as w]
            [plumbing.fnk.pfnk :as pfnk])
  (:import [clojure.lang Var IPersistentMap Symbol PersistentVector]))

;;
;; Definitions
;;

(s/defschema Function
  (s/=> {s/Keyword s/Any} s/Any))

(s/defschema KeywordMap
  {s/Keyword s/Any})

(s/defschema Handler
  "Action handler metadata"
  {:fn Function
   :name s/Keyword
   :type s/Keyword
   :ns s/Keyword
   :user KeywordMap
   :description (s/maybe s/Str)
   :input s/Any
   :output s/Any
   (s/optional-key :source-map) {:line s/Int
                                 :column s/Int
                                 :file s/Str
                                 :ns s/Symbol
                                 :name s/Symbol}
   s/Keyword s/Any})

(s/defschema Kekkonen
  {:handlers KeywordMap
   :context KeywordMap
   s/Keyword s/Any})

;;
;; Handlers
;;

(s/defn handler [meta :- KeywordMap, f :- Function]
  (vary-meta f merge {:handler true} meta))

(defn handler? [x]
  (and (map? x) (:fn x) (:type x)))

;;
;; Type Resolution
;;

(s/defn type-resolver [& types :- [s/Keyword]]
  (fn [meta]
    (reduce
      (fn [_ type]
        (if (or (some-> meta type true?) (some-> meta :type (= type)))
          (reduced (-> meta (assoc :type type) (dissoc type)))))
      nil types)))

(def default-type-resolver (type-resolver :handler))

;;
;; Collecting
;;

(s/defn ^:private user-meta [v :- (s/either Var Function)]
  (-> v meta (dissoc :schema :handler :ns :name :file :column :line :doc :description :plumbing.fnk.impl/positional-info)))

(defprotocol HandlerCollector
  (-collect [this type-resolver]))

(s/defn collect
  ([collector]
    (collect collector default-type-resolver))
  ([collector type-resolver]
    (-collect collector type-resolver)))

(defrecord CollectVar [v]
  HandlerCollector
  (-collect [_ type-resolver]
    (when-let [{:keys [line column file ns name doc schema type]} (type-resolver (meta v))]
      (if (and name schema)
        {(keyword name) {:fn @v
                         :type type
                         :name (keyword name)
                         :user (user-meta v)
                         :description doc
                         :input (pfnk/input-schema @v)
                         :output (pfnk/output-schema @v)
                         :source-map {:line line
                                      :column column
                                      :file file
                                      :ns (ns-name ns)
                                      :name name}}}))))

(defn collect-var [v]
  (->CollectVar v))

(defrecord CollectFn [f]
  HandlerCollector
  (-collect [_ type-resolver]
    (if-let [{:keys [name description schema type]} (type-resolver (meta f))]
      (if (and name schema)
        {:fn f
         :type type
         :name (keyword name)
         :user (user-meta f)
         :description description
         :input (pfnk/input-schema f)
         :output (pfnk/output-schema f)}))))

(defn collect-fn [f]
  (->CollectFn f))

(defrecord CollectNs [ns]
  HandlerCollector
  (-collect [_ type-resolver]
    (require ns)
    (some->> ns
             ns-publics
             (map (comp collect-var val))
             (keep #(-collect % type-resolver))
             (apply merge))))

(defn collect-ns [ns]
  (->CollectNs ns))

(extend-type IPersistentMap
  HandlerCollector
  (-collect [this type-resolver]
    (p/for-map [[k v] this]
      k (-collect v type-resolver))))

(extend-type Var
  HandlerCollector
  (-collect [this type-resolver]
    (-collect (collect-var this) type-resolver)))

(extend-type Symbol
  HandlerCollector
  (-collect [this type-resolver]
    (-collect (collect-ns this) type-resolver)))

(extend-type PersistentVector
  HandlerCollector
  (-collect [this type-resolver]
    (->> this
         (map #(-collect % type-resolver))
         (apply merge))))

;;
;; Registry
;;

(p/defnk create :- Kekkonen
  "Creates a Kekkonen."
  [handlers :- KeywordMap
   {type-resolver :- s/Any default-type-resolver}
   {context :- {s/Keyword s/Any} {}}]
  (letfn [(enrich [h m]
            (if (seq m)
              (let [ns (->> m (map name) (str/join "/") keyword)]
                (assoc (type-resolver h) :ns ns))
              (throw (ex-info "can't define handlers into empty namespace" {:handler h}))))
          (traverse [x m]
            (p/for-map [[k v] x]
              k (if (handler? v)
                  (enrich v m)
                  (traverse v (conj m k)))))]
    {:context context
     :handlers (traverse (collect handlers type-resolver) [])}))

(s/defn ^:private action-kws [path :- s/Keyword]
  (-> path str (subs 1) (str/split #"/") (->> (mapv keyword))))

(s/defn some-handler :- (s/maybe Handler)
  "Returns a handler or nil"
  [kekkonen, action :- s/Keyword]
  (get-in (:handlers kekkonen) (action-kws action)))

(s/defn all-handlers :- [Handler]
  "Returns all handlers."
  [kekkonen]
  (let [handlers (atom [])]
    (w/prewalk
      (fn [x]
        (if (handler? x)
          (do (swap! handlers conj x) nil)
          x))
      (:handlers kekkonen))
    @handlers))

(s/defn invoke
  "Invokes a action handler with the given context."
  ([kekkonen action]
    (invoke kekkonen action {}))
  ([kekkonen action context]
    (if-let [handler (some-handler kekkonen action)]
      ((:fn handler) (merge (:context kekkonen) context))
      (throw (ex-info (str "Invalid action " action) {})))))

(comment
  (p/defnk ^:handler tst [])

  ; long
  (./aprint (collect {:abba (collect-ns 'kekkonen.core)} default-type-resolver))

  ; short
  (./aprint (collect 'kekkonen.core))

  ; short version
  (create {:handlers {:test 'kekkonen.core}})

  ; long version
  (create
    {:context {:components {:db (atom #{})}}
     :handlers {:test 'kekkonen.core}})

  (def k (create {:handlers {:test 'kekkonen.core}}))

  (./aprint k)

  (./aprint
    (all-handlers k)))
