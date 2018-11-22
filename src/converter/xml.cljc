(ns converter.xml
  (:require
   #?(:clj [clojure.java.io :as io])
   #?(:cljs [cljs-node-io.core :as io])
   #?(:cljs [converter.xmldom])
   [clojure.data.xml :as xml]
   [clojure.string :as str]
   [converter.spec :as spec]
   [spec-tools.core :as st]
   [spec-tools.transform :as stt]))

(defn strip-whitespace
  [xml]
  (if (map? xml)
    {:tag (:tag xml) :attrs (:attrs xml) :content (strip-whitespace (:content xml))}
    (if (seq? xml)
      (for [x xml :when (not (str/blank? x))] (strip-whitespace x))
      xml)))

(defn xml?
  [xml]
  (xml/element? xml))

(defn encode
  [xml]
  (xml/emit-str xml))

(defn decode
  [xml-str]
  #?(:clj
     (xml/parse-str xml-str :skip-whitespace true)
     :cljs
     (strip-whitespace
      (xml/parse-str xml-str))))