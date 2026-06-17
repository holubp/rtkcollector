# Terminology

## Receiver RX

Bytes emitted by the GNSS receiver and read by RtkCollector. These bytes are
recorded byte-exactly in `receiver-rx.raw`.

## App TX To Receiver

Bytes transmitted by RtkCollector toward the receiver serial input. This
includes init commands, shutdown commands and correction bytes sent to the
receiver. These bytes are recorded separately from receiver RX.

## Correction Input

Bytes received by RtkCollector from NTRIP or another correction source before
they are forwarded to a receiver or advisory solution engine.

## Temporary Base

A stationary receiver session used to determine a coordinate from RTK against
another base, PPP, static/post-processing or fallback averaging.

## Fixed Base

A receiver configured with an already accepted coordinate to produce base
corrections. Fixed-base operation is separate from temporary-base coordinate
determination.

## Device Internal Solution

The receiver's own navigation solution, such as UM980 BESTNAV/ADRNAV/PPPNAV or
u-blox NAV-PVT-derived status.

## In-Phone Solution

A solution computed by the Android device, such as future RTKLIB real-time
processing. V1 does not require in-phone RTKLIB.

