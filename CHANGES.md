## 0.3.0-SNAPSHOT

* **BREAKING**: Removed type-level interceptors from ring-adapter, use normal interceptors instead.
* **BREAKING**: Ring request-parameters are now assoc-in'd (into `:data`) instead of deep-merging. For speed.
* Handlers can be now be mounted to dispatcher root.
* Removed `kekkonen.core/simple-coercion`, renamed `multi-coercion` to `coercion`.
* Support for Context-based urls, thanks to [Wout Neirynck](https://github.com/wneirynck).
* Data input schemas for apis can be vectors, fixes [#27](https://github.com/metosin/kekkonen/issues/27).
* Use Pedestal-style interceptors, with `:name`, `:enter`, `:leave` and `:error`
  * Extended to contain `:input` and `:output` schemas.
* Exceptions raised in the interceptor chain are rethrown as wrapped (Pedestal) exceptions,
containing extra meta-data of the failed step: `:execution-id`, `:stage`, `:interceptor`, `:exception-type` and `:exception`.
  * extra fields are removed in the api exception handling
* `kekkonen.core/request` & `kekkonen.core/response` exception handeled gracefully with the `api`s
* Createing an `api` doesn't force schema validation by default
* Interceptors are pre-compiled into Records in all layers for simplicity and better perf.
* Remove the following excess meta-data from handlers: 
  * `:ns-meta`, `:all-meta`, `:handler-input` & `:user-input`
* Remove `:interceptors` from the `Dispatcher`, as they are now precompiled into handlers
* Interceptors can be `nil`, allowing conditional interceptors

```clj
(k/handler
  {:name "fixture!"
   :interceptors [[require-role :admin] (if-not env/dev-mode? log-it)]
   :handle (fn [ctx] ...)})
```

* **BREAKING**: top-level swagger options are now in align to the compojure-api:
  * Fixes [#22](https://github.com/metosin/kekkonen/issues/22)
  * By default, `api`s don't bind swagger-spec & swagger-ui, use `:spec` & `:ui` options

### Old

```clj
{:swagger {:info {:title "Kekkonen"}}
 :swagger-ui {:jsonEdit true}})
```

### New

```clj
{:swagger
 {:spec "/swagger.json"
  :ui "/api-docs"
  :options {:ui {:jsonEdit true}
            :spec {:ignore-missing-mappings? false}}
  :data {:info {:title "Kekkonen"}}}}
```

* **BREAKING**: Handler dispatch function is now `:handle` instead of `:function`
* Handlers can be defined via a single map with `:handle` key for the dispatch

```clj
(k/handler
  {:name "hello"
   :handle (constantly "hello")})
```

* updated dependencies:

```clj
[prismatic/schema "1.1.2"] is available but we use "1.1.0"
[prismatic/plumbing "0.5.3"] is available but we use "0.5.2"
[metosin/ring-http-response "0.7.0"] is available but we use "0.6.5"
[metosin/ring-swagger "0.22.9"] is available but we use "0.22.6"
[clj-http "2.2.0"] available but we use "2.1.0"
```

## 0.2.0 (29.3.2016)

**[compare](https://github.com/metosin/kekkonen/compare/0.1.2...0.2.0)**

* Change Transformers to (initial version of) Interceptors in both the Dispatcher & Ring.
  * `:transformers`-key is replaced with `:interceptors`
  * Interceptors are either functions `context => context` (just like the old transformers) or maps
  with keys `:enter` and `:leave`. Will be later merged to use the [Pedestal](http://pedestal.io/) defined
  interceptors.
* User defined context-handers are now under `:meta` instead of `:user`.
* Defined `:meta` keys are checked at dispatcher creation time.
* By default, dispatcher will have a  `:interceptors` meta-key registered.
  * It takes an vector of interceptors as value, applied first to the namespace/handler
* Ring-adapter interceptors can use the dispatcher context, fixes [#26](https://github.com/metosin/kekkonen/issues/26)

#### Old syntax

```clj
(cqrs-api
  {:swagger {:info {:title "Kekkonen"}}
   :core {:handlers {:api {:math 'math
                           :system [#'ping #'pong]}}
          :transformers [log-commands]
          :user {::roles require-roles}}
   :ring {:transformers [api-key-authenticator]}})
```

#### New syntax

```clj
(cqrs-api
  {:swagger {:info {:title "Kekkonen"}}
   :core {:handlers {:api {:math 'math
                           :system [#'ping #'pong]}}
          :interceptors [log-commands]
          :meta {::roles require-roles}}
   :ring {:interceptors [api-key-authenticator]}})
```

* Updated dependencies

```clj
[prismatic/schema "1.1.0"] is available but we use "1.0.4"
[metosin/ring-swagger "0.22.6"] is available but we use "0.22.1"
[metosin/ring-swagger-ui "2.1.4-0"] is available but we use "2.1.3-4"
[clj-http "2.1.0"] is available but we use "2.0.0"
```

## 0.1.2 (30.12.2015)

* Fix client using wrong keywords [#16](https://github.com/metosin/kekkonen/pull/16)
* Deterministic order for user annotations [#15](https://github.com/metosin/kekkonen/pull/15)

```clj
[prismatic/schema "1.0.4"] is available but we use "1.0.3"
[metosin/ring-swagger "0.22.1"] is available but we use "0.22.0"
[metosin/ring-swagger-ui "2.1.3-4"] is available but we use "2.1.3-2"
```

## 0.1.1 (25.11.2015)

- Fix Transit format options

## 0.1.0 (10.11.2015)

- Initial public version
