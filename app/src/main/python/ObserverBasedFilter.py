'''
Rensselaer Polytechnic Institute - Julius Lab
SenSE Project
Author - Chukwuemeka Osaretin Ike

Description:
'''
import itertools
import numpy as np

from math import pi, floor
from scipy import signal
from scipy.fft import fft
from scipy.linalg import expm


INT_MAX = 2147483647


class ObserverBasedFilter:
    '''
        Observer-Based Filter Class that houses all the necessary functions for 
        running and optimizing an external filter (in Kotlin).

        Think of this class as a single place we can change simulation and 
        optimization parameters. 
        
        It returns filter outputs and optimized params as needed in the
        Kotlin class.
    '''
    # Filter parameters
    _omg = 2*pi/24
    _zeta = 1
    _gamma_d = 1
    _order = 3
    _stateLength = (2 * _order + 1)

    # Create the autonomous A matrix given specific size. Currently hardcoded to dt=1/60
    # TODO: Make this better
    Ac = np.zeros([_stateLength, _stateLength])
    for k in range(_order):
        i = (1 + k) * 2
        k = k + 1 #handles the one off error
        Ac[i - 2:i, i - 2:i] = [[0, 1], [float(-(k * _omg) ** 2), 0]]
    _A_auto = expm(Ac*1/60)
    
    # Optimization hyperparameters
    _mu = 100
    _rho = 2
    _lambda = 50
    _max_iterations = 25

    # Bounds on the filter params used for optimization
    _LB = -5
    _lStart = 0
    _rEnd = 8
    _mid = (_lStart+_rEnd)/2

    def estimateAverageDailyPhase(self, xHat1: np.ndarray, xHat2: np.ndarray, numDays: int, numDataPointsPerDay: int) -> np.ndarray:
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
        # print(xHat1)
        # print(xHat2.shape)
        theta = np.mod(-np.arctan2(x2, self._omg*x1) + pi/2, 2*pi) - pi
        # print(theta.shape)

        averageDailyPhase = np.zeros([1, numDays])
        day1RangeStart = 0
        day1RangeEnd = numDataPointsPerDay

        for i in range(0, numDays):
            day2RangeStart = (numDataPointsPerDay*i)
            day2RangeEnd = (numDataPointsPerDay*(i+1))
            # print(day2RangeStart, day2RangeEnd)
            # print((theta[0,day2RangeStart:day2RangeEnd]))
            # print(np.unwrap(theta[day2RangeStart:day2RangeEnd]))
            # print((np.unwrap(theta[day2RangeStart:day2RangeEnd])).shape)
            # print(np.unwrap(theta[day1RangeStart:day1RangeEnd]) - np.unwrap(theta[day2RangeStart:day2RangeEnd]))
            # print((np.unwrap(theta[day1RangeStart:day1RangeEnd]) - np.unwrap(theta[day2RangeStart:day2RangeEnd])).shape)
            # print((1/self._omg)*np.mean( np.unwrap(theta[day1RangeStart:day1RangeEnd]) - np.unwrap(theta[day2RangeStart:day2RangeEnd]), axis=1))

            averageDailyPhase[0, i] = (1/self._omg)*np.mean( np.unwrap(theta[0, day1RangeStart:day1RangeEnd]) - np.unwrap(theta[0, day2RangeStart:day2RangeEnd]), axis=0)

        averageDailyPhase = np.mod(12+averageDailyPhase, 24) - 12
        # print(xHat1.shape)
        # print(averageDailyPhase)
        return averageDailyPhase

    def simulateDynamics(self, t:np.ndarray, y: np.ndarray, A: np.ndarray, B: np.ndarray, C: np.ndarray, D: np.ndarray) -> np.ndarray:
        '''Simulates the system dynamics on the input data [y].

        Args:
            t (np.ndarray) - time values (in hours from first entry) for the data
            y (np.ndarray) - biometric data values
            A (np.ndarray) - discrete-time A matrix
            B (np.ndarray) - discrete-time B matrix
            C (np.ndarray) - discrete-time C matrix
            D (np.ndarray) - discrete-time D matrix
        Returns:
            filterOutput(np.ndarray) - contains [xHat; yHat]:
                xHat (np.ndarray) - filter state estimates 
                yHat (np.ndarray) - filter output
        '''
        xHat = np.zeros([self._stateLength, len(y)])
        xHat[self._stateLength-1, 0] = 70

        for j in range(1, len(y)):
            # TODO: Make sure this is as efficient as possible
            # The reshapes are all to make sure it's the right dimension
            # Update the current state based on previous filter state and previous input
            if y[j-1] == 0:
                xHat[:,j] = np.reshape(np.matmul(self._A_auto,xHat[:,j-1]).T, (self._stateLength))
            else:
                xHat[:,j] = np.reshape(np.reshape(np.matmul(A,xHat[:,j-1]).T, (self._stateLength,1)) + (B*y[j-1]), (self._stateLength))
        
        # Multiply the whole history of filter states by the C matrix to give us the output history
        yHat = np.matmul(C, xHat)

        return np.append(xHat, yHat, axis=0)

    def optimizeFilter(self, t:np.ndarray, y: np.ndarray) -> np.ndarray:
        '''Optimizes the filter given input time and value data

        Args:
            t (np.ndarray) - time (in hours from first entry) values for the data
            y (np.ndarray) - biometric data values
        Returns:
            L (np.ndarray) - optimal gain matrix
        '''

        # Random number generator for randomizing the combinations of population members
        rng = np.random.default_rng()

        # # For testing a specific L against MATLAB values - correct as of 01/17/2022
        # L = np.array([[0], [.0086], [.0339]])
        # A,B,C,D = self.createStateSpace(t, L)
        # print(A,B,C,D)
        # print(np.absolute(np.linalg.eig(A)[0]))
        # print(np.absolute(np.linalg.eig(A)[0]) > 1)
        # print(self._checkStability(A))

        # Create initial population for optimization
        population = self._initializePopulation()
        cost = np.zeros([self._mu, 1])
        # avgCost = np.zeros([self._max_iterations, 1])
        newGen = np.zeros([self._lambda, population.shape[1]])
        newCost = np.zeros([self._lambda, 1])

        # Generate sequential combinations of [1:mu]
        combinations = np.array(list(itertools.combinations(range(0, self._mu ), self._rho)))

        # Compute the original spectrum for calculating costs of filter outputs
        originalSpectrum, f, _ = self._computeSpectrum(t, y)

        # Compute costs of the initial population
        for member in range(0, self._mu):
            L = population[member, :].reshape(self._stateLength, 1)
            A, B, C, D = self.createStateSpace(t, L)

            # Only simulateDynamics if the system is stable
            if not self._checkStability(A):
                cost[member] = INT_MAX
                continue
            
            # Simulate the system's dynamics and retain yHat
            out = self.simulateDynamics(t, y, A, B, C, D)
            # print(out.shape)
            yHat = out[-1,:]

            # Compute the output spectrum and corresponding cost
            filteredSpectrum, _, _ = self._computeSpectrum(t, yHat)
            filteredSpectrum = filteredSpectrum.reshape(originalSpectrum.shape)
            cost[member] = self._computeCost(originalSpectrum, filteredSpectrum, f)
        
        # Run the optimization for _max_iterations
        for iteration in range(0, self._max_iterations):
            # Randomize the rows of combinations
            # Noah's was repeating integers (bad because it would randomly give certain elements more weight than they might deserve)
            # labels = np.random.randint(1, len(combinations), len(combinations)) 
            labels = rng.choice(len(combinations), len(combinations), replace=False)

            # Create _lambda new offspring and calculate their costs
            for j in range(self._lambda):
                # Create offspring with a random pair from combinations
                newGen[j, :] = np.mean(population[combinations[labels[j], :], :], axis=0).reshape(1,self._stateLength)

                # Create the state space with the new offspring
                L = newGen[j, :].reshape(self._stateLength, 1)
                A, B, C, D = self.createStateSpace(t, L)
                
                # Only simulateDynamics if the system is stable
                if not self._checkStability(A):
                    newCost[j] = INT_MAX
                    continue
                
                # Simulate the system's dynamics and retain yHat
                out = self.simulateDynamics(t, y, A, B, C, D)
                yHat = out[-1,:]

                # Compute the output spectrum and corresponding cost
                filteredSpectrum, _, _ = self._computeSpectrum(t, yHat)
                filteredSpectrum = filteredSpectrum.reshape(originalSpectrum.shape)
                newCost[j] = self._computeCost(originalSpectrum, filteredSpectrum, f)
            
            # Append the newGen and newCost to allow us work on one array each
            population = np.append(population, newGen, axis=0)
            cost = np.append(cost, newCost)

            # Remove the lambda highest costs from both cost and population
            maxIndex = np.argpartition(cost, -self._lambda)[-self._lambda:]
            
            cost = np.delete(cost, maxIndex)
            population = np.delete(population, maxIndex, axis=0)

            # # Take the average cost - useful when trying to visualize
            # avgCost[iteration] = np.mean(cost)

        # Return the best gain in the final population as L
        idx = np.argmin(cost)
        return population[idx, :].reshape(1, self._stateLength) # Returning this shape to ease Kotlin PyObject conversion
            
    def createStateSpace(self, t:np.ndarray, L: np.ndarray):
        '''Creates discrete-time state space given the time vector and gain matrix.

            Args: 
                t (np.ndarray) - time (in hours from first entry) values for the data
                L (np.ndarray) - gain matrix 
            Returns:
                A (np.ndarray) - discrete-time A matrix
                B (np.ndarray) - discrete-time B matrix
                C (np.ndarray) - discrete-time C matrix
                D (np.ndarray) - discrete-time D matrix
        '''
        
        L = np.array(L).reshape(self._stateLength, 1)       # Ensure L is an np array of correct dimension

        Ac = np.zeros([self._stateLength, self._stateLength])
        Cc = np.zeros([1, self._stateLength])
        Dc = 0

        # Populate State matrices in slices.
        for k in range(self._order):
            i = (1 + k) * 2
            k = k + 1 #handles the one off error
            Ac[i - 2:i, i - 2:i] = [[0, 1], [float(-(k * self._omg) ** 2), 0]]
            Cc[0][i-1] = (2 * self._zeta) / (k * self._omg)

        # Assign edge values.
        Cc[0][len(Cc[0])-1] = 1

        A = (Ac - L*Cc)
        B = L
        C = Cc
        D = Dc

        # Uses the "scipy" library and converts the continuous matrixes into a SS
        #   -Then takes the continuous system and decritizes it (NOTE: method for c2d may not match model)
        dt = t[1] - t[0]    # Sampling time for discretization
        discSystem = signal.StateSpace(A, B, C, D).to_discrete(dt)

        # Return the discrete system because we're only using discrete for the app 
        return discSystem.A, discSystem.B, discSystem.C, discSystem.D

    def _initializePopulation(self):
        '''
            Creates the initial population using a log scale sampling (detailed in README and paper)
            Returns:
                population (np.ndarray) - the initial population to use in the filter optimization
        '''
        N = (self._rEnd-self._lStart)*np.random.rand(self._mu, self._stateLength) + self._lStart

        diff = 0.5 

        population = np.zeros(N.shape)
        population[N > self._mid+diff] = 10**(N[N > self._mid+diff] - self._mid + self._LB)
        population[N < self._mid-diff] = -10**(self._mid - N[N < self._mid-diff] + self._LB)
        
        return population
    
    def _computeSpectrum(self, t: np.ndarray, y: np.ndarray) -> np.ndarray:
        '''
            Computes the frequency spectrum of the input [y] sampled according to [t]
            Args:
                t (np.ndarray) - time values for the data
                y (np.ndarray) - biometric data values
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
        P = P2[0:floor(lent/2)]
        P[1:len(P)-1] = 2*P[1:len(P)-1]
        f = Fs*np.arange(0, floor(lent/2))/lent

        return P, f, lent

    def _computeCost(self, originalSpectrum: np.ndarray, filteredSpectrum: np.ndarray, f: np.ndarray) -> float:
        '''
            Computes the cost of the filteredSpectrum by comparing it with the originalSpectrum
            Args:
                originalSpectrum (np.ndarray) - frequency spectrum of the original signal
                filteredSpectrum (np.ndarray) - frequency spectrum of the output filtered signal
                f (np.ndarray) - frequency values corresponding to originalSpectrum
            Returns:
                cost (float) - the cost of the filteredSpectrum - discrepancy from originalSpectrum
        '''
        N1 = np.argmin(abs(f - (1/24)))
        N2 = np.argmin(abs(f - (0.0289)))
        NN = N1 - N2

        # Calculate the square error within the band around each 
        # specified harmonic and the DC term
        J_harmo = np.trapz(np.square((filteredSpectrum[0:NN] - originalSpectrum[0:NN]))) +\
                        np.trapz(np.square(filteredSpectrum[N1-NN:N1+NN+1] - originalSpectrum[N1-NN:N1+NN+1]))
        J_noise = np.trapz(np.square(filteredSpectrum[NN+1:N1-NN])) + np.trapz(np.square(originalSpectrum[N1+NN+1:]))

        return J_harmo + J_noise
    
    def _checkStability(self, A) -> bool:
        '''
            Checks if the dynamics are stable by making sure the eigenvalues are in the 
            unit circle (assuming a discrete-time system)
            Args:
                A (np.ndarray) - dynamics matrix
            Returns:
                isStable (bool) - whether the dynamics are stable or not
        '''
        # If any eigenvalue magnitude is greater than 1, not stable
        if np.sum(np.absolute(np.linalg.eig(A)[0]) > 1) > 0:
            return False
        else:
            return True