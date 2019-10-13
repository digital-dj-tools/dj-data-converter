(ns converter.app
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   [converter.rekordbox.core :as r]
   [converter.spec :as spec]
   [converter.traktor.core :as t]
   [converter.xml :as xml]
   [spec-tools.core :as st]))

(defprotocol TraktorRekordboxConverter
  (input-spec [this])
  (input-string-transformer [this])
  (input-xml-transformer [this])
  (library-spec [this])
  (output-spec [this progress])
  (output-xml-transformer [this]))

(def traktor->rekordbox
  (reify
    TraktorRekordboxConverter
    (input-spec
      [this]
      (t/nml-spec))
    (input-string-transformer
      [this]
      t/string-transformer)
    (input-xml-transformer
      [this]
      t/xml-transformer)
    (library-spec
      [this]
      t/library-spec)
    (output-spec
      [this progress]
      (r/dj-playlists-spec progress))
    (output-xml-transformer
     [this]
     r/xml-transformer)))

(defn doto-prn
  [obj f]
  (prn (f obj)))

(s/fdef convert
  :args (s/cat :config #{{:converter traktor->rekordbox}}
               :xml (spec/value-encoded-spec (t/nml-spec) t/string-transformer))
  :ret (spec/value-encoded-spec r/dj-playlists-spec r/string-transformer))
; TODO :ret spec should OR with some spec that checks all leafs are strings

(defn convert
  [config xml]
  (let [converter (:converter config)
        input-spec (input-spec converter)
        library-spec (library-spec converter)
        output-spec (output-spec converter (:progress config))]
    (as-> xml $
      (spec/decode! input-spec $ (input-string-transformer converter))
      (spec/decode! library-spec $ (input-xml-transformer converter))
      (st/encode output-spec $ (output-xml-transformer converter)))))