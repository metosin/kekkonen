(ns kekkonen.core
  (:require [schema.core :as s]
            [plumbing.core :as p]
            [clojure.string :as str]
            [clojure.walk :as w]
            [plumbing.fnk.pfnk :as pfnk]
            [kekkonen.common :as kc])
  (:import [clojure.lang Var IPersistentMap Symbol PersistentVector AFunction]))

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

(s/defn ^:private user-meta [meta :- KeywordMap]
  (-> meta (dissoc :type :schema :ns :name :file :column :line :doc :description :plumbing.fnk.impl/positional-info)))

(defprotocol CollectHandlers
  (-collect [this type-resolver]))

(s/defn collect
  ([collector]
    (collect collector default-type-resolver))
  ([collector type-resolver]
    (-collect collector type-resolver)))

(defn- -collect-var [v type-resolver]
  (when-let [{:keys [line column file ns name doc schema type] :as meta} (type-resolver (meta v))]
    (if (and name schema)
      {(keyword name) {:fn @v
                       :type type
                       :name (keyword name)
                       :user (user-meta meta)
                       :description doc
                       :input (pfnk/input-schema @v)
                       :output (pfnk/output-schema @v)
                       :source-map {:line line
                                    :column column
                                    :file file
                                    :ns (ns-name ns)
                                    :name name}}})))

(defn- -collect-fn [f type-resolver]
  (if-let [{:keys [name description schema type] :as meta} (type-resolver (meta f))]
    (if (and name schema)
      {:fn f
       :type type
       :name (keyword name)
       :user (user-meta meta)
       :description description
       :input (pfnk/input-schema f)
       :output (pfnk/output-schema f)})))

(defn- -collect-ns [ns type-resolver]
  (require ns)
  (some->> ns
           ns-publics
           (map (comp #(-collect-var % type-resolver) val))
           (apply merge)))

(extend-type AFunction
  CollectHandlers
  (-collect [this type-resolver]
    (-collect-fn this type-resolver)))

(extend-type Var
  CollectHandlers
  (-collect [this type-resolver]
    (-collect-var this type-resolver)))

(extend-type Symbol
  CollectHandlers
  (-collect [this type-resolver]
    (-collect-ns this type-resolver)))

(extend-type PersistentVector
  CollectHandlers
  (-collect [this type-resolver]
    (->> this
         (map #(-collect % type-resolver))
         (apply merge))))

(extend-type IPersistentMap
  CollectHandlers
  (-collect [this type-resolver]
    (p/for-map [[k v] this]
      k (-collect v type-resolver))))

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
                (assoc h :ns ns))
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
      (let [context (kc/deep-merge (:context kekkonen) context)]
        ((:fn handler) context))
      (throw (ex-info (str "Invalid action " action) {})))))

(defn with-context [kekkonen context]
  (update-in kekkonen [:context] kc/deep-merge context))
