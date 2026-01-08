

    
    arr = [0] * 1000

    for i in range(0, 1000):
        arr[i] = random.randint(0,1000)
    
    n = len(arr)

    for i in range(n):
        swapped = False

        for j in range(0, n-i-1):
            if (arr[j] > arr[j + 1]):
                tmp = arr[j]
                arr[j] = arr[j + 1]
                arr[j + 1] = tmp
                swapped = True

        if not swapped:
            break
