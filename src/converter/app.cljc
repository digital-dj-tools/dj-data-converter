(ns converter.app
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   [converter.config :as config]
   [converter.rekordbox.core :as r]
   [converter.spec :as spec]
   [converter.traktor.core :as t]
   [converter.xml :as xml]
   [spec-tools.core :as st]))

(defprotocol Converter
  (input-spec [this config])
  (library-spec [this])
  (output-spec [this config]))

(def traktor->rekordbox
  (reify
    Converter
    (input-spec
      [this config]
      (t/nml-spec config))
    (library-spec
      [this]
      t/library-spec)
    (output-spec
      [this config]
      (r/dj-playlists-spec config))))

(def rekordbox->traktor
  (reify
    Converter
    (input-spec
      [this config]
      (r/dj-playlists-spec config))
    (library-spec
      [this]
      r/library-spec)
    (output-spec
      [this config]
      (t/nml-spec config))))

(defn doto-prn
  [obj f]
  (prn (f obj)))

(s/fdef convert
  :args (s/cat 
         :converter #{traktor->rekordbox}
         :config config/config-spec
         :xml (spec/value-encoded-spec (t/nml-spec {}) spec/string-transformer))
  :ret (spec/value-encoded-spec (r/dj-playlists-spec {}) spec/string-transformer))
; TODO :ret spec should OR with some spec that checks all leafs are strings

(defn convert
  [converter config xml]
  (let [input-spec (input-spec converter config)
        library-spec (library-spec converter)
        output-spec (output-spec converter config)]
    (as-> xml $
      (spec/decode! input-spec $ spec/string-transformer)
      (spec/decode! library-spec $ spec/xml-transformer)
      (st/encode output-spec $ spec/xml-transformer))))