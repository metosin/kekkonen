## 0.3.0-SNAPSHOT

* **BREAKING**: Removed type-level interceptors from ring-adapter.
* Support for Context-based urls, thanks to [Wout Neirynck](https://github.com/wneirynck).
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
[prismatic/schema "1.1.1"] is available but we use "1.1.0"
[prismatic/plumbing "0.5.3"] is available but we use "0.5.2"
[metosin/ring-swagger "0.22.7"] is available but we use "0.22.6"
[clj-http "3.1.0"] available but we use "2.1.0"
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
