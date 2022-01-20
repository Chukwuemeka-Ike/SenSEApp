from math import pi, floor
import numpy as np
from scipy import signal
from scipy.fft import fft
import itertools

INT_MAX = 2147483647

class ObserverBasedFilter:
    '''
        Observer-Based Filter Class that houses all the necessary functions for 
        running and optimizing an external filter (in Kotlin).

        Think of this class as a single place we can change simulation and 
        optimization parameters. 
        
        It returns filter outputs and optimized gains as needed in the 
        Kotlin class.
    '''
    # Filter parameters
    __omg = 2*pi/24
    __zeta = 1
    __gamma_d = 1
    __order = 1
    __stateLength = (2 * __order + 1)
    
    # Optimization hyperparameters
    __mu = 10
    __rho = 2
    __lambda = 5
    __max_iterations = 5

    # Bounds on the filter gains used for optimization
    __LB = -5
    __lStart = 0
    __rEnd = 8
    __mid = (__lStart+__rEnd)/2

    def simulateDynamics(self, t:np.ndarray, y: np.ndarray, A: np.ndarray, B: np.ndarray, C: np.ndarray, D: np.ndarray) -> np.ndarray:
        '''
            Simulates the system dynamics on the input data [y]
            Parameters:
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
        xHat = np.zeros([self.__stateLength, len(y)], dtype=float)

        for j in range(1, len(y)):
            # TODO: Make sure this is as efficient as possible
            # The reshapes are all to make sure it's the right dimension
            # Update the current state based on previous filter state and previous input
            xHat[:,j] = np.reshape(np.reshape(np.matmul(A,xHat[:,j-1]).T, (self.__stateLength,1)) + (B*y[j-1]), (self.__stateLength))
        
        # Multiply the whole history of filter states by the C matrix to give us the output history
        yHat = np.matmul(C, xHat)

        return np.append(xHat, yHat, axis=0)

    def optimizeFilter(self, t:np.ndarray, y: np.ndarray) -> np.ndarray:
        '''
            Optimizes the filter given input time and value data
            Parameters:
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
        # print(self.__checkStability(A))

        # Create initial population for optimization
        population = self.__initializePopulation()
        cost = np.zeros([self.__mu, 1], dtype=float)
        # avgCost = np.zeros([self.__max_iterations, 1], dtype=float)
        newGen = np.zeros([self.__lambda, population.shape[1]], dtype=float)
        newCost = np.zeros([self.__lambda, 1], dtype=float)

        # Generate sequential combinations of [1:mu]
        combinations = np.array(list(itertools.combinations(range(0, self.__mu ), self.__rho)))

        # Compute the original spectrum for calculating costs of filter outputs
        originalSpectrum, f, _ = self.__computeSpectrum(t, y)

        # Compute costs of the initial population
        for member in range(0, self.__mu):
            L = population[member, :].reshape(self.__stateLength, 1)
            A, B, C, D = self.createStateSpace(t, L)

            # Only simulateDynamics if the system is stable
            if not self.__checkStability(A):
                cost[member] = INT_MAX
                continue
            
            # Simulate the system's dynamics and retain yHat
            out = self.simulateDynamics(t, y, A, B, C, D)
            # print(out.shape)
            yHat = out[-1,:]

            # Compute the output spectrum and corresponding cost
            filteredSpectrum = self.__computeSpectrum(t, yHat)
            cost[member] = self.__computeCost(originalSpectrum, filteredSpectrum, f)
        
        # Run the optimization for __max_iterations
        for iteration in range(0, self.__max_iterations):
            # Randomize the rows of combinations
            # Noah's was repeating integers (bad because it would randomly give certain elements more weight than they might deserve)
            # labels = np.random.randint(1, len(combinations), len(combinations)) 
            labels = rng.choice(len(combinations), len(combinations), replace=False)

            # Create __lambda new offspring and calculate their costs
            for j in range(self.__lambda):
                # Create offspring with a random pair from combinations
                newGen[j, :] = np.mean(population[combinations[labels[j], :], :], axis=0).reshape(1,self.__stateLength)

                # Create the state space with the new offspring
                L = newGen[j, :].reshape(self.__stateLength, 1)
                A, B, C, D = self.createStateSpace(t, L)
                
                # Only simulateDynamics if the system is stable
                if not self.__checkStability(A):
                    newCost[j] = INT_MAX
                    continue
                
                # Simulate the system's dynamics and retain yHat
                out = self.simulateDynamics(t, y, A, B, C, D)
                yHat = out[-1,:]

                # Compute the output spectrum and corresponding cost
                filteredSpectrum = self.__computeSpectrum(t, yHat)
                newCost[j] = self.__computeCost(originalSpectrum, filteredSpectrum, f)
            
            # Append the newGen and newCost to allow us work on one array each
            population = np.append(population, newGen, axis=0)
            cost = np.append(cost, newCost)

            # Remove the lambda highest costs from both cost and population
            maxIndex = np.argpartition(cost, -self.__lambda)[-self.__lambda:]
            
            cost = np.delete(cost, maxIndex)
            population = np.delete(population, maxIndex, axis=0)

            # # Take the average cost - useful when trying to visualize
            # avgCost[iteration] = np.mean(cost)

        # Return the best gain in the final population as L
        idx = np.argmin(cost)
        return population[idx, :].reshape(1, self.__stateLength) # Returning this shape to ease Kotlin PyObject conversion
            
    def createStateSpace(self, t:np.ndarray, L: np.ndarray):
        '''
            Creates discrete-time state space given the time vector and gain matrix
            Parameters: 
                t (np.ndarray) - time (in hours from first entry) values for the data
                L (np.ndarray) - gain matrix 
            Returns:
                A (np.ndarray) - discrete-time A matrix
                B (np.ndarray) - discrete-time B matrix
                C (np.ndarray) - discrete-time C matrix
                D (np.ndarray) - discrete-time D matrix
        '''
        
        L = np.array(L).reshape(self.__stateLength, 1)       # Ensure L is an np array of correct dimension

        Ac = np.zeros([self.__stateLength, self.__stateLength], dtype = float)
        # Bc = np.zeros([self.__stateLength, 1], dtype = float)
        Cc = np.zeros([1, self.__stateLength], dtype = float)
        Dc = 0

        # Populate State matrices in slices
        for k in range(self.__order):
            i = (1 + k) * 2
            k = k + 1 #handles the one off error
            Ac[i - 2:i, i - 2:i] = [[0, 1], [float(-(k * self.__omg) ** 2), 0]]
            # Bc[i-2:i] = [[0],[(k*self.__omg)**2]]
            Cc[0][i-1] = (2 * self.__zeta) / (k * self.__omg)

        # Assign edge values
        # Bc[len(Bc)-1][0] = self.__gamma_d
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

    def __initializePopulation(self):
        '''
            Creates the initial population using a log scale sampling (detailed in README and paper)
            Returns:
                population (np.ndarray) - the initial population to use in the filter optimization
        '''
        N = (self.__rEnd-self.__lStart)*np.random.rand(self.__mu, self.__stateLength) + self.__lStart

        diff = 0.5 

        population = np.zeros(N.shape)
        population[N > self.__mid+diff] = 10**(N[N > self.__mid+diff] - self.__mid + self.__LB)
        population[N < self.__mid-diff] = -10**(self.__mid - N[N < self.__mid-diff] + self.__LB)
        
        return population
    
    def __computeSpectrum(self, t: np.ndarray, y: np.ndarray) -> np.ndarray:
        '''
            Computes the frequency spectrum of the input [y] sampled according to [t]
            Parameters:
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

    def __computeCost(self, originalSpectrum: np.ndarray, filteredSpectrum: np.ndarray, f: np.ndarray) -> float:
        '''
            Computes the cost of the filteredSpectrum by comparing it with the originalSpectrum
            Parameters:
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
    
    def __checkStability(self, A) -> bool:
        '''
            Checks if the dynamics are stable by making sure the eigenvalues are in the 
            unit circle (assuming a discrete-time system)
            Parameters:
                A (np.ndarray) - dynamics matrix
            Returns:
                isStable (bool) - whether the dynamics are stable or not
        '''
        # If any eigenvalue magnitude is greater than 1, not stable
        if np.sum(np.absolute(np.linalg.eig(A)[0]) > 1) > 0:
            return False
        else:
            return True