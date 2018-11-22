(ns converter.xmldom
  (:require [cljs.nodejs]))

(set! js/DOMParser
      (.-DOMParser (cljs.nodejs/require "xmldom")))

(set! js/XMLSerializer
      (.-XMLSerializer (cljs.nodejs/require "xmldom")))