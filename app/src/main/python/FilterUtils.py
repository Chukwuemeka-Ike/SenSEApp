import numpy as np

from math import pi, floor
from scipy.fft import fft
from typing import Tuple


def computeSpectrum(t: np.ndarray, y: np.ndarray) -> Tuple[np.ndarray, np.ndarray, int]:
    '''Computes frequency spectrum of the input [y] sampled according to [t].

    Args:
        t: time values for the data
        y: - biometric data values
    Returns:
        P (np.ndarray) - Single-sided frequency spectrum of the data
        f (np.ndarray) - Frequency values corresponding to [P]
        lent (int) - length of the time vector
    '''
    T = t[1] - t[0]
    Fs = 1/T
    lent = len(t)
    Y = fft(y)
    P2 = abs(Y / lent)
    P = P2[0:floor(lent/2)+1]
    P[1:-1] = 2*P[1:-1]
    f = Fs*np.arange(0, floor(lent/2))/lent

    return P, f, lent

def computeCost(originalSpectrum: np.ndarray, filteredSpectrum: np.ndarray,
                f: np.ndarray, order: int) -> float:
    '''Computes the cost by comparing filtered with original.

    Args:
        originalSpectrum: freq spectrum of the original signal.
        filteredSpectrum: freq spectrum of the filtered signal.
        f: frequency values corresponding to originalSpectrum.
        order: filter order for cost computation.
    Returns:
        cost (float) - the cost of the filteredSpectrum - discrepancy from originalSpectrum
    '''
    # Get the indices of f closest to each harmonic.
    N1 = np.argmin(abs(f - (1/24)))
    N2 = np.argmin(abs(f - (2/24)))
    N3 = np.argmin(abs(f - (3/24)))
    N4 = np.argmin(abs(f - (4/24)))
    N5 = np.argmin(abs(f - (5/24)))
    N6 = np.argmin(abs(f - (6/24)))
    n1 = np.argmin(abs(f - 0.0309))
    NN = N1 - n1
    harmonicIdxs = [N1, N2, N3, N4, N5, N6]

    # J_harmo is the square error within the band around each specified harmonic
    # and the DC term.
    J_harmo = np.trapz(
        np.square((filteredSpectrum[0:NN] - originalSpectrum[0:NN]))
    ) # DC component.

    # J_noise is the square of the signal outside the bands around each
    # harmonic and beyond the last one.
    J_noise = np.trapz(np.square(filteredSpectrum[NN:N1-NN])) # DC to 1st.

    for i in range(order):
        idx = harmonicIdxs[i]
        J_harmo = J_harmo + \
            np.trapz(np.square(
                filteredSpectrum[idx-NN:idx+NN] -\
                originalSpectrum[idx-NN:idx+NN]
            ))
        if i < order-1:
            idx2 = harmonicIdxs[i+1]
            J_noise = J_noise +\
                np.trapz(np.square(
                    filteredSpectrum[idx+NN:idx2-NN]
                ))
    J_noise = J_noise + np.trapz(np.square(filteredSpectrum[idx+NN:]))

    return J_harmo + J_noise

def estimateAverageDailyPhase(xHat1: np.ndarray, xHat2: np.ndarray,
                              numDays: int, numDataPointsPerDay: int,
                              omg: float) -> np.ndarray:
    '''Computes the frequency spectrum of the input [y] sampled according to [t].

    Args:
        xHat (np.ndarray) - filter state 1 and 2 
        numDays (int) - number of days 
        numDataPointsPerDay (int) - number of data points per day - 1440 for 1-minute intervals
    Returns:
        averageDailyPhase (np.ndarray) - array of average daily phase difference from day 1 in hours
    '''
    x1 = xHat1
    x2 = xHat2
    theta = np.mod(-np.arctan2(x2, omg*x1) + pi/2, 2*pi) - pi

    averageDailyPhase = np.zeros([1, numDays], dtype = float)
    day1RangeStart = 0
    day1RangeEnd = numDataPointsPerDay

    for i in range(0, numDays):
        day2RangeStart = (numDataPointsPerDay*i)
        day2RangeEnd = (numDataPointsPerDay*(i+1))

        averageDailyPhase[0, i] = (1/omg)*np.mean(
            np.unwrap(theta[0, day1RangeStart:day1RangeEnd]) -\
            np.unwrap(theta[0, day2RangeStart:day2RangeEnd]),
            axis=0
        )

    averageDailyPhase = np.mod(12+averageDailyPhase, 24) - 12
    return averageDailyPhase