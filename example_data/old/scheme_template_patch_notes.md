# Patch for 122Ag_final_evaluated_logft_limits.txt

Apply these edits to the uploaded PostScript/TXT scheme file.

## Mother-state label

Replace:

```postscript
/mSpinpar ((3+), (1-), (9-)) def				%spin and parity of mother nuclues
```

with:

```postscript
/mSpinpar ((1-), (9-)) def				%spin and parity of mother nuclues
```

## Delete level lines

Delete these accepted-scheme level lines:

```postscript
(7.53)	(0.036)			(3061.70)		((8+))		3061.70	()		0	0	level
()	()			(3169.71)		()		3169.71	()		-1	0	level
(>6.34)	(0.481)			(3266.52)		()		3266.52	()		0	1	level
```

## Delete transition lines

Delete these transition lines from the accepted scheme:

```postscript
()	()	(867.5 (<0.5))	()			  2196.41	1328.92	50	1	0	trans
()	()	(884.1 (<0.5))	()			  3061.70	2177.64	50	1	0	trans
()	()	(667.6 (<0.5))	()			  3169.71	2502.11	50	1	0	trans
()	()	(622.4 (0.5))	()			  3266.52	2644.12	50	0	1	trans
```

## Replace transition intensity labels

Replace:

```postscript
()	()	(465.5 (<0.5))	()			  2444.47	1978.97	50	1	0	trans
()	()	(466.4 (6.7))	()			  2644.12	2177.64	50	0	0	trans
()	()	(665.3 (9.2))	()			  2644.12	1978.97	50	0	0	trans
```

with:

```postscript
()	()	(465.5 (2.0))	()			  2444.47	1978.97	50	1	0	trans
()	()	(466.4 (4.7))	()			  2644.12	2177.64	50	0	0	trans
()	()	(665.3 (6.4))	()			  2644.12	1978.97	50	0	0	trans
```

ABF and log ft values are intentionally not recalculated in this patch.
