(set! *warn-on-reflection* true)
(ns converter.xml
  (:require
   [clojure.data.xml :as xml]
   [clojure.string :as string]))

(defn strip-whitespace
  [xml]
  (if (map? xml)
    {:tag (:tag xml) :attrs (:attrs xml) :content (strip-whitespace (:content xml))}
    (if (seq? xml)
      (for [x xml :when (not (string/blank? x))] (strip-whitespace x))
      xml)))

(defn xml?
  [xml]
  (xml/element? xml))

(defn encode
  [xml]
  (xml/emit-str xml))

(defn decode
  [xml-str]
  (xml/parse-str xml-str :skip-whitespace true))